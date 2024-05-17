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

package org.gradle.invocation;

import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Collects and isolates the actions provided via the {@link org.gradle.api.invocation.GradleLifecycle GradleLifecycle} API.
 */
@ServiceScope(Scope.Build.class)
public interface IsolatedProjectEvaluationListenerProvider {

    /**
     * @see org.gradle.api.invocation.GradleLifecycle#beforeProject(IsolatedAction)
     */
    void beforeProject(IsolatedAction<? super Project> action);

    /**
     * @see org.gradle.api.invocation.GradleLifecycle#afterProject(IsolatedAction)
     */
    void afterProject(IsolatedAction<? super Project> action);

    /**
     * Returns an isolated listener for the registered actions, if any. The listener makes it impossible for
     * the actions to carry any shared mutable state across projects and can be safely executed in parallel.
     */
    @Nullable
    ProjectEvaluationListener isolateFor(Gradle owner);

    /**
     * Discards any registered actions. This doesn't affect any {@link #isolateFor(Gradle) previously returned isolated listeners}.
     */
    void clear();
}
