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

package com.netflix.spinnaker.orca.actorsystem.execution;

import akka.actor.SupervisorStrategy;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.actorsystem.stage.StageActor;
import com.netflix.spinnaker.orca.actorsystem.stage.StageId;
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage;
import com.netflix.spinnaker.orca.actorsystem.task.TaskId;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.context.ApplicationContext;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

import static akka.actor.ActorRef.noSender;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ExecutionActor extends AbstractPersistentActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public static final FiniteDuration DEFAULT_BACKOFF_PERIOD = Duration.create(100, MILLISECONDS);

  private final ExecutionRepository executionRepository;

  private ExecutionId identifier;
  private Collection<StageId> executingStageIds;

  public ExecutionActor(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @Override
  public String persistenceId() {
    return format("Pipeline-%s", self().path().name());
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveCommand() {
    return ReceiveBuilder
      .match(ExecutionMessage.RequestPipeline.class, this::onPipelineRequested)
      .match(ExecutionMessage.ExecutePipeline.class, command -> runPipeline())
      .match(SupervisorStrategy.Stop$.class, this::onShutdown)
      .build();
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveRecover() {
    return ReceiveBuilder
      .match(ExecutionRequested.class, this::updateState)
      .match(RecoveryCompleted.class, this::onRecoveryCompleted)
      .build();
  }

  private void runPipeline() {
    Pipeline pipeline = getPipeline();

    log.info("Running Pipeline {}", pipeline.getName());

    pipeline
      .getStages()
      .stream()
      .filter(stage -> stage.getStatus() == ExecutionStatus.NOT_STARTED)
      .forEach(stage -> {
        ClusterSharding.get(context().system()).shardRegion(StageActor.class.getSimpleName()).tell(
          new StageMessage.RequestStage(stage.getExecution().getId(), stage.getId()),
          noSender()
        );
      });
  }

  private Pipeline getPipeline() {
    return executionRepository.retrievePipeline(identifier.executionId);
  }

  private void onPipelineRequested(ExecutionMessage.RequestPipeline command) {
    if (identifier == null) {
      persist(new ExecutionRequested(command.id()), event -> {
        updateState(event);
        runPipeline();
      });
    } else {
      log.info("Pipeline {} was already requested", identifier);
    }
  }

  private void onRecoveryCompleted(RecoveryCompleted event) {
    if (identifier != null) {
      log.debug("Recovery completed with pipeline id {}", identifier.executionId);
      context().parent().tell(new ExecutionMessage.ExecutePipeline(identifier), noSender());
    } else {
      log.debug("Recovery completed but no pipeline id");
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

  private void updateState(ExecutionRequested event) {
    identifier = event.id;
  }

  private interface Event extends Serializable {
  }

  private static class ExecutionRequested implements Event {
    private final ExecutionId id;

    private ExecutionRequested(ExecutionId id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ExecutionRequested that = (ExecutionRequested) o;
      return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }
}
