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

package com.netflix.spinnaker.orca.actorsystem.stage;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.actorsystem.execution.ExecutionId;
import com.netflix.spinnaker.orca.actorsystem.execution.ExecutionMessage;
import com.netflix.spinnaker.orca.actorsystem.task.TaskId;

import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;

public abstract class StageMessage {

  private final StageId id;

  protected StageMessage(StageId id) {
    this.id = id;
  }

  public StageId id() {
    return id;
  }

  public static class RequestStage extends StageMessage {
    public RequestStage(StageId identifier) {
      super(identifier);
    }

    public RequestStage(String executionId, String stageId) {
      this(new StageId(executionId, stageId));
    }
  }

  public static class ExecuteStage extends StageMessage {
    public ExecuteStage(StageId identifier) {
      super(identifier);
    }

    public ExecuteStage(String executionId, String stageId) {
      this(new StageId(executionId, stageId));
    }
  }

  public static abstract class TaskStatusUpdate extends StageMessage {

    final TaskId taskId;
    final ExecutionStatus status;

    public TaskStatusUpdate(TaskId id, ExecutionStatus status) {
      super(id.stage());
      this.taskId = id;
      this.status = status;
    }

    public ExecutionStatus status() {
      return status;
    }

    public TaskId task() {
      return taskId;
    }
  }

  /**
   * Task has completed execution either successfully or not.
   */
  public static class TaskComplete extends TaskStatusUpdate {
    public TaskComplete(TaskId id, ExecutionStatus status) {
      super(id, status);
    }
  }

  /**
   * Task is not yet complete.
   */
  public static class TaskIncomplete extends TaskStatusUpdate {
    public TaskIncomplete(TaskId id, ExecutionStatus status) {
      super(id, status);
    }
  }

  /**
   * Task has been cancelled because the execution has been cancelled.
   */
  public static class TaskCancelled extends TaskStatusUpdate {
    public TaskCancelled(TaskId id) {
      super(id, CANCELED);
    }
  }

  /**
   * Task is being skipped because it already had a completed status.
   */
  public static class TaskSkipped extends TaskStatusUpdate {
    public TaskSkipped(TaskId id, ExecutionStatus status) {
      super(id, status);
    }
  }
}
