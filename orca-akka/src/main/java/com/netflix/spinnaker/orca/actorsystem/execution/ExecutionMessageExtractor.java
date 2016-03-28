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

import akka.cluster.sharding.ShardRegion;

import static java.lang.String.format;

public class ExecutionMessageExtractor extends ShardRegion.HashCodeMessageExtractor {
  public ExecutionMessageExtractor() {
    super(100);
  }

  @Override
  public String entityId(Object message) {
    if (message instanceof ExecutionMessage) {
      return ((ExecutionMessage) message).id().toString();
    } else {
      throw new IllegalArgumentException(format("Message type %s not supported", message.getClass()));
    }
  }
}
