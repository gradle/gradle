/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

/**
 * Supplies task execution information.
 */
public interface TaskIdentityProvider {

    /**
     * Returns the identity of the task under which the target build operation is running.
     *
     * @param id the id of the target build operation
     * @return the task identity or {@code null}, if the operation is not running in the context  of a task
     */
    @Nullable
    TaskIdentity taskIdentityFor(OperationIdentifier id);
}
