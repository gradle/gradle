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

package org.gradle.composite.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.internal.Actions;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultBuildableCompositeBuildContext implements CompositeBuildContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildableCompositeBuildContext.class);

    // TODO: Synchronization
    private final Map<ProjectComponentIdentifier, RegisteredProject> projectMetadata = Maps.newHashMap();
    private final Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> provided = Sets.newHashSet();
    private final Set<BuildIdentifier> configuredBuilds = Sets.newHashSet();
    private final List<Action<DependencySubstitution>> substitutionRules = Lists.newArrayList();
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder;
    private IncludedBuildRegistry includedBuildRegistry;


    public DefaultBuildableCompositeBuildContext(ImmutableModuleIdentifierFactory moduleIdentifierFactory, IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.dependencyMetadataBuilder = dependencyMetadataBuilder;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = getRegisteredProject(project);
        return registeredProject != null ? registeredProject.metaData : null;
    }

    public Collection<LocalComponentArtifactMetadata> getAdditionalArtifacts(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = getRegisteredProject(project);
        return registeredProject != null ? registeredProject.artifacts : null;
     }

    @Override
    public void registerSubstitution(ModuleVersionIdentifier moduleId, ProjectComponentIdentifier project) {
        LOGGER.info("Registering " + project + " in composite build. Will substitute for module '" + moduleId.getModule() + "'.");
        provided.add(Pair.of(moduleId, project));
    }

    @Override
    public void registerSubstitution(Action<DependencySubstitution> substitutions) {
        substitutionRules.add(substitutions);
    }

    @Override
    public Action<DependencySubstitution> getRuleAction() {
        List<Action<DependencySubstitution>> allActions = Lists.newArrayList();
        if (!provided.isEmpty()) {
            allActions.add(new CompositeBuildDependencySubstitutions(provided, moduleIdentifierFactory));
        }
        allActions.addAll(substitutionRules);
        return Actions.composite(allActions);
    }

    @Override
    public boolean hasRules() {
        return !(provided.isEmpty() && substitutionRules.isEmpty());
    }

    private RegisteredProject getRegisteredProject(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        BuildIdentifier buildIdentifier = project.getBuild();
        if (registeredProject == null && !configuredBuilds.contains(buildIdentifier)) {
            // TODO: This shouldn't rely on the state of configuredBuilds to figure out whether or not we should configure this build again
            // This is to prevent a recursive loop through this when we're configuring the build
            configuredBuilds.add(buildIdentifier);
            IncludedBuildInternal includedBuild = (IncludedBuildInternal) includedBuildRegistry.getBuild(buildIdentifier);
            if (includedBuild != null) {
                projectMetadata.putAll(dependencyMetadataBuilder.build(includedBuild));
                registeredProject = projectMetadata.get(project);
                if (registeredProject == null) {
                    throw new IllegalStateException(project + " was not found.");
                }
            }
        }
        return registeredProject;
    }

    @Override
    public void setIncludedBuildRegistry(IncludedBuildRegistry includedBuildRegistry) {
        this.includedBuildRegistry = includedBuildRegistry;
    }
}
