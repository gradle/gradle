/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.operations.dependencies.transforms;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Executing a transform action.
 * <p>
 * Info about the owning transform execution can be inferred, and we don't provide any further info at this point.
 * This is largely to expose timing information about executed transforms
 *
 * @since 8.3
 */
public final class ExecuteTransformActionBuildOperationType implements BuildOperationType<ExecuteTransformActionBuildOperationType.Details, ExecuteTransformActionBuildOperationType.Result> {

    public interface Details {
    }

    public interface Result {
    }

    public static final Details DETAILS_INSTANCE = new Details() {};
    public static final Result RESULT_INSTANCE = new Result() {};
}
