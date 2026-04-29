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

import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateLookup;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.Path;

@ServiceScope({Scope.Build.class, Scope.Project.class})
public class DefaultProjectDependencyFactory {
    private final Instantiator instantiator;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final ObjectFactory objectFactory;
    private final AttributesFactory attributesFactory;
    private final ProjectStateLookup projectStateLookup;
    private final ProjectFinder projectFinder;

    /**
     * Creates a DefaultProjectDependencyFactory configured with the collaborators required to construct
     * and configure project dependencies.
     *
     * @param instantiator                    used to instantiate DefaultProjectDependency instances
     * @param capabilityNotationParser        parses capability notation into Capability objects for dependencies
     * @param objectFactory                   supplies objects used within created dependencies
     * @param attributesFactory               provides attribute handling for created dependencies
     * @param projectStateLookup              resolves a project's ProjectState by its identity path
     * @param projectFinder                   resolves string project paths to identity Path instances
     */
    public DefaultProjectDependencyFactory(
        Instantiator instantiator,
        CapabilityNotationParser capabilityNotationParser,
        ObjectFactory objectFactory,
        AttributesFactory attributesFactory,
        ProjectStateLookup projectStateLookup,
        ProjectFinder projectFinder
    ) {
        this.instantiator = instantiator;
        this.capabilityNotationParser = capabilityNotationParser;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.projectStateLookup = projectStateLookup;
        this.projectFinder = projectFinder;
    }

    public ProjectDependency create(ProjectState projectState) {
        DefaultProjectDependency projectDependency = instantiator.newInstance(DefaultProjectDependency.class, projectState);
        injectServices(projectDependency);
        return projectDependency;
    }

    /**
     * Creates a ProjectDependency for the project identified by the given project path.
     *
     * @param projectPath the project path string to resolve (e.g. ":sub:project")
     * @return the ProjectDependency for the project resolved from {@code projectPath}
     * @throws UnknownProjectException if no project exists for the provided path
     */
    public ProjectDependency create(String projectPath) {
        return create(projectFinder.resolveIdentityPath(projectPath));
    }

    /**
     * Create a ProjectDependency for the project identified by the given identity path.
     *
     * @param projectIdentityPath the identity path of the target project
     * @return the configured ProjectDependency for the specified project
     * @throws UnknownProjectException if no project exists with the given identity path
     */
    public ProjectDependency create(Path projectIdentityPath) {
        ProjectState projectState = projectStateLookup.findProject(projectIdentityPath);
        if (projectState == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", projectIdentityPath.asString()));
        }
        return create(projectState);
    }

    private void injectServices(DefaultProjectDependency projectDependency) {
        projectDependency.setAttributesFactory(attributesFactory);
        projectDependency.setCapabilityNotationParser(capabilityNotationParser);
        projectDependency.setObjectFactory(objectFactory);
    }

}
