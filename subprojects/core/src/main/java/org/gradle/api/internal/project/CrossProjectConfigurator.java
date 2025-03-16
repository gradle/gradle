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
import org.gradle.api.internal.MutationGuard;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildSession.class)
public interface CrossProjectConfigurator {

    void project(ProjectInternal project, Action<? super Project> configureAction);

    void subprojects(Iterable<? extends ProjectInternal> projects, Action<? super Project> configureAction);

    void allprojects(Iterable<? extends ProjectInternal> projects, Action<? super Project> configureAction);

    void rootProject(ProjectInternal project, Action<? super Project> buildOperationExecutor);

    /**
     * Tracks whether the current thread is executing a lazy operation on a domain
     * object within this project.
     */
    MutationGuard getLazyBehaviorGuard();

}
