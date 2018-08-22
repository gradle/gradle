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

package org.gradle.api.internal.project;

import org.gradle.api.Action;
import org.gradle.api.Project;

public interface CrossProjectConfigurator {

    Project project(Project project, Action<? super Project> configureAction);

    void subprojects(Iterable<Project> projects, Action<? super Project> configureAction);

    void allprojects(Iterable<Project> projects, Action<? super Project> configureAction);

    Project rootProject(Project project, Action<Project> buildOperationExecutor);

    /**
     * Asserts that project mutating methods are currently allowed at the point in time this method is called.
     */
    void assertProjectMutationAllowed(String methodName, Object target);

    /**
     * Wraps the configuration action to disallow certain project mutating methods from being called while executing.
     * The intent is for this method to be used wherever configuration code could be invoked at execution time (or later)
     * to prevent unsafe patterns such as calling {@link Project#afterEvaluate(Action)} after the configuration phase
     * has finished.
     *
     * @param action the delegated action
     * @param <T> the type the action is mutating
     * @return action that disallows cross-project configuration.
     */
    <T> Action<T> withProjectMutationDisabled(Action<? super T> action);

}
