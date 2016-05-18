/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.mahe.pipeline

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mahe.tasks.CreatePropertiesTask
import com.netflix.spinnaker.orca.mahe.tasks.MonitorPropertiesTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.getType

@Slf4j
@Component
class CreatePropertyStage implements StageDefinitionBuilder, CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = getType(CreatePropertyStage)

  @Autowired MonitorCreatePropertyStage monitorCreatePropertyStage

  @Override
  List<StageDefinitionBuilder.TaskDefinition> taskGraph() {
    return [
      new StageDefinitionBuilder.TaskDefinition("createProperties", CreatePropertiesTask),
      new StageDefinitionBuilder.TaskDefinition("monitorProperties", MonitorPropertiesTask)
    ]
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
//    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")
//
//    def deletedProperties = deletePropertyTask.execute(stage)
//
//    return new CancellableStage.Result(stage, [
//       deletedPropertyIdList: stage.context.propertyIdList,
//       deletedPropertiesResults: deletedProperties
//    ])
    return null
  }
}
