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

package org.gradle.api.internal.artifacts;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.reflect.Instantiator;

public class DefaultProjectDependencyFactory {
    private final ProjectAccessListener projectAccessListener;
    private final Instantiator instantiator;
    private final boolean buildProjectDependencies;

    public DefaultProjectDependencyFactory(ProjectAccessListener projectAccessListener, Instantiator instantiator, boolean buildProjectDependencies) {
        this.projectAccessListener = projectAccessListener;
        this.instantiator = instantiator;
        this.buildProjectDependencies = buildProjectDependencies;
    }

    public ProjectDependency create(ProjectInternal project, String configuration) {
        return instantiator.newInstance(DefaultProjectDependency.class, project, configuration, projectAccessListener, buildProjectDependencies);
    }

    public ProjectDependency create(Project project) {
        return instantiator.newInstance(DefaultProjectDependency.class, project, projectAccessListener, buildProjectDependencies);
    }
}
