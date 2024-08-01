/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.configurations.ProjectComponentObservationListener;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.event.ListenerManager;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A simple dependency-management scoped wrapper around {@link BuildTreeLocalComponentProvider} that
 * tracks which domain object context makes a given project component request. The primary
 * purpose of this class is to track dependencies between projects as they are resolved. By knowing which
 * project is making the request, we can determine which projects depend on which other projects.
 */
public class DefaultLocalComponentRegistry implements LocalComponentRegistry {
    private final Path currentProjectPath;
    private final Path currentBuildPath;
    private final ProjectComponentObservationListener projectComponentObservationListener;
    private final BuildTreeLocalComponentProvider componentProvider;

    @Inject
    public DefaultLocalComponentRegistry(
        DomainObjectContext domainObjectContext,
        ListenerManager listenerManager,
        BuildTreeLocalComponentProvider componentProvider
    ) {
        this.currentProjectPath = getProjectBuildTreePath(domainObjectContext);
        this.currentBuildPath = domainObjectContext.getBuildPath();
        this.projectComponentObservationListener = listenerManager.getBroadcaster(ProjectComponentObservationListener.class);
        this.componentProvider = componentProvider;
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectComponentIdentifier projectIdentifier) {
        Path targetProjectPath = ((ProjectComponentIdentifierInternal) projectIdentifier).getIdentityPath();
        if (!targetProjectPath.equals(currentProjectPath)) {

            // TODO: We should relax this check. For legacy reasons we are not tracking cross-build project
            // dependencies, but we should be. Removing this condition breaks some Isolated Projects tests,
            // so we need to investigate why they are failing and then remove this condition.
            // Specifically, the following test breaks when we remove this check:
            // IsolatedProjectsToolingApiIdeaProjectIntegrationTest.ensures unique name for all Idea modules in composite
            if (projectIdentifier.getBuild().getBuildPath().equals(currentBuildPath.getPath())) {
                projectComponentObservationListener.projectObserved(currentProjectPath, targetProjectPath);
            }

        }

        return componentProvider.getComponent(projectIdentifier, currentBuildPath);
    }

    @Nullable
    private static Path getProjectBuildTreePath(DomainObjectContext domainObjectContext) {
        ProjectIdentity id = domainObjectContext.getProjectIdentity();
        if (id != null) {
            return id.getBuildTreePath();
        }

        return null;
    }
}
