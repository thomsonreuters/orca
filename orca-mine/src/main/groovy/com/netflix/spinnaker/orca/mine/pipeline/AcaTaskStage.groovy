/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorAcaTaskTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterAcaTaskTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class AcaTaskStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired
  MineService mineService

  @Override
  List<StageDefinitionBuilder.TaskDefinition> taskGraph() {
    return Arrays.asList(
      new StageDefinitionBuilder.TaskDefinition("registerGenericCanary", RegisterAcaTaskTask),
      new StageDefinitionBuilder.TaskDefinition("monitorGenericCanary", MonitorAcaTaskTask),
      new StageDefinitionBuilder.TaskDefinition("completeCanary", CompleteCanaryTask)
    );
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    def cancelCanaryResults = mineService.cancelCanary(stage.context.canary.id as String, "Pipeline execution (${stage.execution?.id}) canceled")
    log.info("Canceled canary in mine (canaryId: ${stage.context.canary.id}, stageId: ${stage.id}, executionId: ${stage.execution.id})")

    return new CancellableStage.Result(stage, ["canary": cancelCanaryResults])
  }
}