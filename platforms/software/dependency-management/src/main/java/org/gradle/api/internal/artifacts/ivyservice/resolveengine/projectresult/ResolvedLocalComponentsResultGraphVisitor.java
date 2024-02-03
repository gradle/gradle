/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to track which configurations in other projects a given resolution depends on. This data is
 * used to mark those configurations as observed so that they cannot be mutated later.
 *
 * <strong>Do not build on-top of this visitor. If you want to track cross-project dependencies,
 * use {@link org.gradle.api.internal.artifacts.configurations.ProjectComponentObservationListener}</strong>
 *
 * TODO: The logic in this visitor should be integrated directly into the DefaultLocalConfigurationMetadataBuilder
 * so that we instantly mark configurations as observed as their metadata is constructed. That would be an improvement
 * over this visitor, since here we only mark a configuration observed if its metadata is present in the final graph.
 * There are likely scenarios that this visitor does not cover, where a configuration's metadata is observed but
 * its component is not present in the final graph.
 */
public class ResolvedLocalComponentsResultGraphVisitor implements DependencyGraphVisitor {
    private final BuildIdentifier thisBuild;
    private final ProjectStateRegistry projectStateRegistry;

    private ComponentIdentifier rootId;
    private final List<ResolvedProjectConfiguration> resolvedProjectConfigurations = new ArrayList<>();

    public ResolvedLocalComponentsResultGraphVisitor(BuildIdentifier thisBuild, ProjectStateRegistry projectStateRegistry) {
        this.thisBuild = thisBuild;
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public void start(RootGraphNode root) {
        this.rootId = root.getOwner().getComponentId();
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        ComponentIdentifier componentId = node.getOwner().getComponentId();
        if (!rootId.equals(componentId) && componentId instanceof ProjectComponentIdentifierInternal) {
            ProjectComponentIdentifierInternal projectComponentId = (ProjectComponentIdentifierInternal) componentId;

            // We should be tracking cross-build configuration observations, but we are not.
            // This check is here for historical reasons, as removing it would be a breaking change.
            // We should just leave this here, since this observation mechanism is being replaced anyway.
            if (projectComponentId.getBuild().equals(thisBuild)) {
                resolvedProjectConfigurations.add(new ResolvedProjectConfiguration(projectComponentId.getIdentityPath(), node.getResolvedConfigurationId().getConfiguration()));
            }
        }
    }

    /**
     * Mark all configurations corresponding to visited project variant nodes as observed.
     */
    public void complete(ConfigurationInternal.InternalState requestedState) {
        for (ResolvedProjectConfiguration projectResult : resolvedProjectConfigurations) {
            ProjectState targetState = projectStateRegistry.stateFor(projectResult.projectIdentity);
            ConfigurationInternal targetConfig = (ConfigurationInternal) targetState.getMutableModel().getConfigurations().findByName(projectResult.targetConfiguration);
            if (targetConfig != null) {
                // Can be null when dependency metadata for target project has been loaded from cache
                targetConfig.markAsObserved(requestedState);
            }
        }
    }

    private static class ResolvedProjectConfiguration {
        private final Path projectIdentity;
        private final String targetConfiguration;

        public ResolvedProjectConfiguration(Path projectIdentity, String targetConfiguration) {
            this.projectIdentity = projectIdentity;
            this.targetConfiguration = targetConfiguration;
        }
    }
}
