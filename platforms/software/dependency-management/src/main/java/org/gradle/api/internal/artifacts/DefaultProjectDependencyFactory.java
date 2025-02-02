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
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.Path;

@ServiceScope({Scope.Build.class, Scope.Project.class})
public class DefaultProjectDependencyFactory {
    private final Instantiator instantiator;
    private final boolean buildProjectDependencies;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final ObjectFactory objectFactory;
    private final AttributesFactory attributesFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ProjectStateRegistry projectStateRegistry;

    public DefaultProjectDependencyFactory(
        Instantiator instantiator,
        boolean buildProjectDependencies,
        NotationParser<Object, Capability> capabilityNotationParser,
        ObjectFactory objectFactory,
        AttributesFactory attributesFactory,
        TaskDependencyFactory taskDependencyFactory,
        ProjectStateRegistry projectStateRegistry
    ) {
        this.instantiator = instantiator;
        this.buildProjectDependencies = buildProjectDependencies;
        this.capabilityNotationParser = capabilityNotationParser;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.projectStateRegistry = projectStateRegistry;
    }

    public ProjectDependency create(Project project) {
        DefaultProjectDependency projectDependency = instantiator.newInstance(DefaultProjectDependency.class, project, buildProjectDependencies, taskDependencyFactory);
        injectServices(projectDependency);
        return projectDependency;
    }

    public ProjectDependency create(Path projectIdentityPath) {
        Project project = projectStateRegistry.stateFor(projectIdentityPath).getMutableModel();
        return create(project);
    }

    private void injectServices(DefaultProjectDependency projectDependency) {
        projectDependency.setAttributesFactory(attributesFactory);
        projectDependency.setCapabilityNotationParser(capabilityNotationParser);
        projectDependency.setObjectFactory(objectFactory);
    }
}
