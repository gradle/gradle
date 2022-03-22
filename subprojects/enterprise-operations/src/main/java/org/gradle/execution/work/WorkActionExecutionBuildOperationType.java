/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.execution.work;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Captures the execution of the actual action of a unit of work. This only includes the execution of the user
 * code belonging to the unit of work, and none of the other operations related to executing the unit like
 * snapshotting, up-to-date checks or build cache operations.
 *
 * The operations can be nested, i.e. when a task executes a worker API action.
 *
 * @see WorkExecutionBuildOperationType
 */
public interface WorkActionExecutionBuildOperationType extends BuildOperationType<WorkActionExecutionBuildOperationType.Details, WorkActionExecutionBuildOperationType.Result> {

    // Info about the owning task can be inferred, and we don't provide any further info at this point.
    // This is largely to expose timing information about executed actions.

    interface Details {
    }

    interface Result {
    }

    Details DETAILS_INSTANCE = new Details() {
    };
    Result RESULT_INSTANCE = new Result() {
    };

}
