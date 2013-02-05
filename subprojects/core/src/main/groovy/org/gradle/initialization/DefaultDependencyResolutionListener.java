/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.internal.project.ProjectInternal;

/**
* by Szczepan Faber, created at: 2/5/13
*/
class DefaultDependencyResolutionListener implements DependencyResolutionListener {

    private BeforeResolve beforeResolve;

    public DefaultDependencyResolutionListener(ProjectAccessListener projectAccessListener) {
        this.beforeResolve = new BeforeResolve(projectAccessListener);
    }

    public void beforeResolve(ResolvableDependencies dependencies) {
        dependencies.getDependencies().all(beforeResolve);
    }

    public void afterResolve(ResolvableDependencies dependencies) {}

    static class BeforeResolve implements Action<Dependency> {
        private final ProjectAccessListener projectAccessListener;

        public BeforeResolve(ProjectAccessListener projectAccessListener) {
            this.projectAccessListener = projectAccessListener;
        }

        public void execute(Dependency dependency) {
            if (dependency instanceof ProjectDependency) {
                Project dependencyProject = ((ProjectDependency) dependency).getDependencyProject();
                projectAccessListener.beforeResolvingProjectDependency((ProjectInternal) dependencyProject);
            }
        }
    }
}