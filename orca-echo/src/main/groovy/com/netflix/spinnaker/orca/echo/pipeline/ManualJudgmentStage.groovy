/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.echo.pipeline

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

import static java.util.Collections.singletonList

@Component
class ManualJudgmentStage implements StageDefinitionBuilder, RestartableStage {

  @Override
  List<StageDefinitionBuilder.TaskDefinition> taskGraph() {
    return [new StageDefinitionBuilder.TaskDefinition("waitForJudgment", WaitForManualJudgmentTask.class)]
  }

  @Override
  Stage prepareStageForRestart(Stage stage) {
    stage = StageDefinitionBuilder.StageDefinitionBuilderSupport.prepareStageForRestart(stage)

    stage.context.remove("judgmentStatus")
    stage.context.remove("lastModifiedBy")
    return stage
  }

  @Slf4j
  @Component
  @VisibleForTesting
  public static class WaitForManualJudgmentTask implements RetryableTask {
    long backoffPeriod = 1000
    long timeout = TimeUnit.DAYS.toMillis(3)

    @Autowired(required = false)
    EchoService echoService

    @Override
    TaskResult execute(Stage stage) {
      def stageData = stage.mapTo(StageData)
      switch (stageData.judgmentStatus.toLowerCase()) {
        case "continue":
          return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
        case "stop":
          def executionStatus = ExecutionStatus.TERMINAL
          return new DefaultTaskResult(executionStatus)
      }

      def outputs = [:]
      if (echoService) {
        outputs = [notifications: stageData.notifications]
        stageData.notifications.findAll { it.shouldNotify() }.each {
          try {
            it.notify(echoService, stage)
          } catch (Exception e) {
            log.error("Unable to send notification (executionId: ${stage.execution.id}, address: ${it.address}, type: ${it.type})", e)
          }
        }
      }

      return new DefaultTaskResult(ExecutionStatus.RUNNING, outputs)
    }
  }

  static class StageData {
    String judgmentStatus = ""
    List<Notification> notifications = []
  }

  static class Notification {
    String address
    String type

    Date lastNotified
    Long notifyEveryMs = -1

    boolean shouldNotify(Date now = new Date()) {
      if (!lastNotified?.time) {
        return true
      }

      if (notifyEveryMs <= 0) {
        return false
      }

      return new Date(lastNotified.time + notifyEveryMs) <= now
    }

    void notify(EchoService echoService, Stage stage) {
      echoService.create(new EchoService.Notification(
        notificationType: EchoService.Notification.Type.valueOf(type.toUpperCase()),
        to: [address],
        templateGroup: "manualJudgment",
        severity: EchoService.Notification.Severity.HIGH,
        source: new EchoService.Notification.Source(
          executionType: stage.execution.class.simpleName.toLowerCase(),
          executionId: stage.execution.id,
          application: stage.execution.application
        ),
        additionalContext: [
          instructions: stage.context.instructions ?: ""
        ]
      ))
      lastNotified = new Date()
    }
  }
}
