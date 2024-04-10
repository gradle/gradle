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

package org.gradle.internal.concurrent;

import org.gradle.api.NonNullApi;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Limits configured for workers in the current build.
 * <p>
 * Workers can be threads, processes or whatever Gradle considers a "worker".
 * <p>
 * Some examples:
 * <ul>
 *     <li>A thread running a task</li>
 *     <li>A test process</li>
 *     <li>A language compiler in a forked process</li>
 * </ul>
 */
@NonNullApi
@ServiceScope(Scope.CrossBuildSession.class)
public interface WorkerLimits {

    /**
     * Returns the maximum number of concurrent workers.
     *
     * @return maximum number of concurrent workers, always &gt;= 1.
     */
    int getMaxWorkerCount();

}
