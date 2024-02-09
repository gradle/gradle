/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.operations.lifecycle;

import org.gradle.internal.operations.BuildOperationType;

/**
 * A build operation around running the requested work of the build.
 * <p>
 * This is the execution phase.
 * The build operation is only fired when you request building tasks.
 *
 * @since 8.7
 */
public final class RunRequestedWorkBuildOperationType implements BuildOperationType<RunRequestedWorkBuildOperationType.Details, RunRequestedWorkBuildOperationType.Result> {
    public interface Details {
    }

    public interface Result {
    }
}
