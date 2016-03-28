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

package com.netflix.spinnaker.orca.batch;

import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import com.netflix.spinnaker.orca.actorsystem.execution.ExecutionActor;
import com.netflix.spinnaker.orca.actorsystem.execution.ExecutionMessage;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;

import static akka.actor.ActorRef.noSender;

public class AkkaExecutionRunner implements ExecutionRunner<Execution> {
  private final ClusterSharding cluster;


  public AkkaExecutionRunner(ClusterSharding cluster) {
    this.cluster = cluster;
  }

  @Override
  public boolean supports(Execution subject) {
    return subject.isRunWithAkka() && subject instanceof Pipeline;
  }

  @Override
  public Execution run(Execution subject) {
    ActorRef region = cluster.shardRegion(ExecutionActor.class.getSimpleName());
    region.tell(new ExecutionMessage.RequestPipeline(subject.getId()), noSender());
    return subject;
  }
}
