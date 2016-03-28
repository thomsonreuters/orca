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

import akka.actor.Props;
import com.netflix.spinnaker.orca.actorsystem.ClusteredActorDefinition;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class ExecutionActorFactory extends AbstractFactoryBean<ClusteredActorDefinition> {

  private final ExecutionRepository repository;

  @Autowired
  public ExecutionActorFactory(
    ExecutionRepository repository) {
    this.repository = repository;
  }

  @Override
  public Class<?> getObjectType() {
    return ClusteredActorDefinition.class;
  }

  @Override
  protected ClusteredActorDefinition createInstance() {
    return ClusteredActorDefinition.create(
      Props.create(ExecutionActor.class, repository),
      new ExecutionMessageExtractor()
    );
  }
}
