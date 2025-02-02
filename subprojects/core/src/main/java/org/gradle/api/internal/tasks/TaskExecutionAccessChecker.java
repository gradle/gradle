/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Checks for tasks accessing the model at execution time, which might be a problem that needs reporting e.g. with configuration cache enabled.
 */
@ServiceScope(Scope.Build.class)
public interface TaskExecutionAccessChecker {
    void notifyProjectAccess(TaskInternal task);
    void notifyTaskDependenciesAccess(TaskInternal task, String invocationDescription);
    void notifyConventionAccess(TaskInternal task, String invocationDescription);
}
