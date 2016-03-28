/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.actorsystem.stage;

import akka.actor.SupervisorStrategy;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.actorsystem.task.TaskActor;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage;
import com.netflix.spinnaker.orca.pipeline.AkkaStepProvider;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.context.ApplicationContext;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static akka.actor.ActorRef.noSender;
import static java.lang.String.format;

@SuppressWarnings("unchecked")
public class StageActor extends AbstractPersistentActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final ApplicationContext applicationContext;
  private final ExecutionRepository executionRepository;

  private StageId identifier;

  public StageActor(ApplicationContext applicationContext, ExecutionRepository executionRepository) {
    this.applicationContext = applicationContext;
    this.executionRepository = executionRepository;
  }

  @Override
  public String persistenceId() {
    return format("Stage-%s", self().path().name());
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveCommand() {
    return ReceiveBuilder
      .match(StageMessage.RequestStage.class, this::onStageRequested)
      .match(StageMessage.TaskIncomplete.class, this::onTaskIncomplete)
      .match(StageMessage.TaskComplete.class, this::onTaskComplete)
      .match(SupervisorStrategy.Stop$.class, this::onShutdown)
      .build();
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveRecover() {
    return ReceiveBuilder
      .match(StageRequested.class, this::updateState)
      .match(RecoveryCompleted.class, this::onRecoveryCompleted)
      .build();
  }

  private void runStage() {
    Stage stage = getStage();

    log.info("Running Stage {}", stage.getName());

    Collection<AkkaStepProvider> akkaStepProviders = applicationContext.getBeansOfType(AkkaStepProvider.class).values();

    akkaStepProviders
      .stream()
      .filter(provider -> provider.getType().equals(stage.getType()))
      .forEach(provider -> {
        if (stage.getTasks().isEmpty()) {
          stage.getTasks().addAll(
            provider
              .buildAkkaSteps(stage)
              .stream()
              .map(stepDefinition -> {
                DefaultTask defaultTask = new DefaultTask();
                defaultTask.setId(stepDefinition.getId());
                defaultTask.setName(stepDefinition.getName());
                defaultTask.setImplementationClass(stepDefinition.getTaskClass().getCanonicalName());
                defaultTask.setStatus(ExecutionStatus.NOT_STARTED);
                return defaultTask;
              })
              .collect(Collectors.toList())
          );
          executionRepository.storeStage(stage);
        }

        List<Task> allTasks = stage.getTasks();
        Optional<Task> nextTask = allTasks
          .stream()
          .filter(t -> t.getStatus().equals(ExecutionStatus.NOT_STARTED))
          .findFirst();

        nextTask.map( task -> {
          try {
            task.setStatus(ExecutionStatus.RUNNING);
            task.setStartTime(System.currentTimeMillis());
            executionRepository.storeStage(stage);

            ClusterSharding.get(context().system()).shardRegion(TaskActor.class.getSimpleName()).tell(
              new TaskMessage.RequestTask(
                (Class<? extends com.netflix.spinnaker.orca.Task>) Class.forName(task.getImplementationClass()),
                stage.getExecution().getId(),
                stage.getId(),
                task.getId()
              ),
              noSender()
            );
          } catch (ClassNotFoundException e) {
            throw new IllegalStateException(format("Unknown classname %s", task.getImplementationClass()), e);
          }
          return task;
        });
      });
  }

  private Stage getStage() {
    Execution execution;

    try {
      execution = executionRepository.retrieveOrchestration(identifier.executionId);
    } catch (ExecutionNotFoundException ignored) {
      execution = executionRepository.retrievePipeline(identifier.executionId);
    }

    List<Stage> stages = execution.getStages();

    return stages
      .stream()
      .filter(stage -> stage.getId().equals(identifier.stageId))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(format("No stage found '%s'", identifier.stageId)));
  }

  private void onStageRequested(StageMessage.RequestStage command) {
    if (identifier == null) {
      persist(new StageRequested(command.id()), event -> {
        updateState(event);
        runStage();
      });
    } else {
      log.info("Stage {} was already requested", identifier);
    }
  }

  private void onTaskIncomplete(StageMessage.TaskIncomplete command) {
    log.info("Task {} is incomplete: {}", command.task().taskId, command.status());
  }


  private void onTaskComplete(StageMessage.TaskComplete command) {
    log.info("Task {} is complete: {}", command.task().taskId, command.status);
    Stage stage = getStage();

    List<Task> allTasks = stage.getTasks();
    Task completedTask = allTasks
      .stream()
      .filter(task -> task.getId().equalsIgnoreCase(command.task().taskId))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No task found"));

    completedTask.setEndTime(System.currentTimeMillis());
    completedTask.setStatus(command.status);

    executionRepository.storeStage(stage);
  }

  private void onRecoveryCompleted(RecoveryCompleted event) {
    if (identifier != null) {
      log.debug("Recovery completed with stage id {}", identifier.stageId);
      context().parent().tell(new StageMessage.ExecuteStage(identifier), noSender());
    } else {
      log.debug("Recovery completed but no stage id");
      shutdown();
    }
  }

  private void onShutdown(SupervisorStrategy.Stop$ command) {
    log.debug("Stopped {}", self().path().name());
    context().stop(self());
  }

  private void shutdown() {
    log.debug("Stopping {}", self().path().name());
    context().parent().tell(new ShardRegion.Passivate(SupervisorStrategy.Stop$.MODULE$), self());
  }

  private void updateState(StageRequested event) {
    identifier = event.id;
  }

  private interface Event extends Serializable {
  }

  private static class StageRequested implements Event {
    private final StageId id;

    private StageRequested(StageId id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StageRequested that = (StageRequested) o;
      return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }
}

