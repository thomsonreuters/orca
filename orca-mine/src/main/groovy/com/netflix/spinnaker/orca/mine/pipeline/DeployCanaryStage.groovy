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

import com.netflix.spinnaker.orca.clouddriver.MortService
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.frigga.NameBuilder
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.FindImageFromClusterTask
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static java.util.Collections.emptyList

@Component
@Slf4j
@CompileStatic
class DeployCanaryStage extends ParallelDeployStage implements CloudProviderAware, CancellableStage {

  public static final String PIPELINE_CONFIG_TYPE = "deployCanary"

  @Autowired
  FindImageFromClusterTask findImage

  @Autowired(required = false)
  List<DiffTask> diffTasks

  @Autowired
  MineService mineService

  @Autowired
  MortService mortService

  @Override
  String getType() {
    PIPELINE_CONFIG_TYPE
  }

  @Override
  <T extends Execution<T>> void postBranchGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("completeDeployCanary", CompleteDeployCanaryTask)
  }

  @Override
  com.netflix.spinnaker.orca.Task completeParallelTask() {
    return new CompleteDeployCanaryTask(Optional.of(diffTasks), mortService)
  }

  @Override
  @CompileDynamic
  <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage) {
    List<Map> baselineAmis = findBaselineAmis(stage)
    Map defaultStageContext = stage.context
    List<Map> canaryDeployments = defaultStageContext.clusterPairs

    return canaryDeployments.collect { Map canaryDeployment ->
      def canary = canaryDeployment.canary
      canary.strategy = "highlander"

      def baseline = canaryDeployment.baseline
      baseline.strategy = "highlander"
      def baselineAmi = baselineAmis.find {
        it.region == (baseline.region ?: baseline.availabilityZones.keySet()[0])
      }
      if (!baselineAmi) {
        throw new IllegalStateException("Could not find an image for the baseline cluster")
      }
      baseline.amiName = baselineAmi?.imageId
      baseline.imageId = baselineAmi?.imageId
      baseline.buildUrl = createBuildUrl(baselineAmi)

      [baseline, canary]
    }.flatten().collect { Map it ->
      clusterContext(stage, defaultStageContext, it)
    }
  }

  @CompileDynamic
  List<Map> findBaselineAmis(Stage stage) {
    Set<String> regions = stage.context.clusterPairs.collect {
      if (it.canary.availabilityZones) {
        it.canary.availabilityZones?.keySet() + it.baseline.availabilityZones?.keySet()
      } else {
        [it.canary.region] + [it.baseline.region]
      }
    }.flatten()

    def findImageCtx = [application: stage.execution.application, account: stage.context.baseline.account, cluster: stage.context.baseline.cluster, regions: regions, cloudProvider: stage.context.baseline.cloudProvider ?: 'aws']
    Stage s = new OrchestrationStage(new Orchestration(), "findImage", findImageCtx)
    TaskResult result = findImage.execute(s)
    try {
      return result.stageOutputs.amiDetails
    } catch (Exception e) {
      throw new IllegalStateException("Could not determine image for baseline deployment (account: ${findImageCtx.account}, " +
        "cluster: ${findImageCtx.cluster}, regions: ${findImageCtx.regions}, " +
        "cloudProvider: ${findImageCtx.cloudProvider})", e)
    }
  }

  @CompileDynamic
  static String createBuildUrl(Map deploymentDetail) {
    def appVersion = AppVersion.parseName(deploymentDetail?.tags?.find {
      it.key == 'appversion'
    }?.value)
    def buildHost = deploymentDetail?.tags?.find {
      it.key == 'build_host'
    }?.value
    if (appVersion && buildHost) {
      return "${buildHost}job/$appVersion.buildJobName/$appVersion.buildNumber/"
    }
    return null
  }

  @Component
  @Slf4j
  static class CompleteDeployCanaryTask implements com.netflix.spinnaker.orca.Task {

    private final List<DiffTask> diffTasks

    private final MortService mortService

    @Autowired
    CompleteDeployCanaryTask(Optional<List<DiffTask>> diffTasks, MortService mortService) {
      this.diffTasks = diffTasks.orElse((List<DiffTask>) emptyList())
      this.mortService = mortService
    }

    @CompileDynamic
    TaskResult execute(Stage stage) {
      def context = stage.context
      def allStages = stage.execution.stages
      def deployStages = allStages.findAll {
        it.parentStageId == stage.id && it.type == ParallelDeployStage.PIPELINE_CONFIG_TYPE
      }
      def deployedClusterPairs = []
      for (Map pair in context.clusterPairs) {
        def resultPair = [canaryStage: context.canaryStageId]
        pair.each { String type, Map cluster ->
          def deployStage = deployStages.find {
            it.context.account == cluster.account &&
              it.context.application == cluster.application &&
              it.context.stack == cluster.stack &&
              it.context.freeFormDetails == cluster.freeFormDetails &&
              (it.context.region && it.context.region == cluster.region ||
                it.context.availabilityZones && it.context.availabilityZones.keySet()[0] == cluster.availabilityZones.keySet()[0])
          }
          def region = cluster.region ?: cluster.availabilityZones.keySet()[0]
          def nameBuilder = new NameBuilder() {
            @Override
            String combineAppStackDetail(String appName, String stack, String detail) {
              return super.combineAppStackDetail(appName, stack, detail)
            }
          }
          if (!cluster.amiName) {
            def ami = deployStage.execution.context.deploymentDetails.find {
              it.region == region
            }

            cluster.amiName = ami?.ami
            cluster.buildUrl = createBuildUrl(ami) ?: ((Pipeline) stage.execution).trigger?.buildInfo?.url
          }

          def accountDetails = mortService.getAccountDetails(cluster.account)

          resultPair[type + "Cluster"] = [
            name          : nameBuilder.combineAppStackDetail(cluster.application, cluster.stack, cluster.freeFormDetails),
            serverGroup   : deployStage.context.'deploy.server.groups'[region].first(),
            accountName   : accountDetails.environment ?: cluster.account,
            type          : cluster.cloudProvider ?: 'aws',
            clusterAccount: cluster.account,
            region        : region,
            imageId       : cluster.amiName,
            buildId       : cluster.buildUrl
          ]
        }
        if (diffTasks) {
          diffTasks.each {
            it.execute(stage)
          }
        }
        deployedClusterPairs << resultPair
      }

      Logger log = LoggerFactory.getLogger(DeployCanaryStage)
      log.info("Completed Canary Deploys")
      Map canary = stage.context.canary
      canary.canaryDeployments = deployedClusterPairs
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [canary: canary, deployedClusterPairs: deployedClusterPairs])
    }
  }

  @Override
  @CompileDynamic
  CancellableStage.Result cancel(Stage stage) {
    def canary = stage.ancestors { Stage s, StageDefinitionBuilder stageBuilder ->
      stageBuilder instanceof CanaryStage
    } first()
    return ((CanaryStage) canary.stageBuilder).cancel(canary.stage)
  }
}

