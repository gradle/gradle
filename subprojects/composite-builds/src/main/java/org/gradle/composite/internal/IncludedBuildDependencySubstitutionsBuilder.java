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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class IncludedBuildDependencySubstitutionsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncludedBuildDependencySubstitutionsBuilder.class);

    private final CompositeBuildContext context;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public IncludedBuildDependencySubstitutionsBuilder(CompositeBuildContext context, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.context = context;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    public void build(IncludedBuildInternal build) {
        DependencySubstitutionsInternal substitutions = resolveDependencySubstitutions(build);
        if (!substitutions.hasRules()) {
            // Configure the included build to discover substitutions
            LOGGER.info("[composite-build] Configuring build: " + build.getProjectDir());
            Gradle gradle = build.getConfiguredBuild();
            for (Project project : gradle.getRootProject().getAllprojects()) {
                registerProject(build, (ProjectInternal) project);
            }
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }

    private void registerProject(IncludedBuild build, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);
        ProjectComponentIdentifier componentIdentifier = newProjectId(build, project.getPath());
        context.registerSubstitution(originalComponent.getId(), componentIdentifier);
    }

    private DependencySubstitutionsInternal resolveDependencySubstitutions(IncludedBuildInternal build) {
        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forIncludedBuild(build, moduleIdentifierFactory);
        for (Action<? super DependencySubstitutions> action : build.getRegisteredDependencySubstitutions()) {
            action.execute(dependencySubstitutions);
        }
        return dependencySubstitutions;
    }

}
