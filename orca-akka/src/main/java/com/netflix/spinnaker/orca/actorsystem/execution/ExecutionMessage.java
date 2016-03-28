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

import java.io.Serializable;

public abstract class ExecutionMessage implements Serializable {
  private final ExecutionId id;

  public ExecutionMessage(ExecutionId id) {
    this.id = id;
  }

  public final ExecutionId id() {
    return id;
  }

  public static class RequestPipeline extends ExecutionMessage {
    public RequestPipeline(ExecutionId identifier) {
      super(identifier);
    }

    public RequestPipeline(String executionId) {
      this(new ExecutionId(executionId));
    }
  }

  public static class RequestOrchestration extends ExecutionMessage {
    public RequestOrchestration(ExecutionId identifier) {
      super(identifier);
    }

    public RequestOrchestration(String executionId) {
      this(new ExecutionId(executionId));
    }
  }

  public static class ExecutePipeline extends ExecutionMessage {
    public ExecutePipeline(ExecutionId identifier) {
      super(identifier);
    }

    public ExecutePipeline(String executionId) {
      this(new ExecutionId(executionId));
    }
  }

  public static class ExecutionOrchestration extends ExecutionMessage {
    public ExecutionOrchestration(ExecutionId identifier) {
      super(identifier);
    }

    public ExecutionOrchestration(String executionId) {
      this(new ExecutionId(executionId));
    }
  }
}
