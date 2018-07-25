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

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.internal.build.IncludedBuildState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncludedBuildDependencySubstitutionsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncludedBuildDependencySubstitutionsBuilder.class);

    private final CompositeBuildContext context;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public IncludedBuildDependencySubstitutionsBuilder(CompositeBuildContext context, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.context = context;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    public void build(IncludedBuildState build) {
        DependencySubstitutionsInternal substitutions = resolveDependencySubstitutions(build);
        if (!substitutions.hasRules()) {
            // Configure the included build to discover available modules
            LOGGER.info("[composite-build] Configuring build: " + build.getModel().getProjectDir());
            context.addAvailableModules(build.getAvailableModules());
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }

    private DependencySubstitutionsInternal resolveDependencySubstitutions(IncludedBuildState build) {
        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forIncludedBuild(build, moduleIdentifierFactory);
        build.getRegisteredDependencySubstitutions().execute(dependencySubstitutions);
        return dependencySubstitutions;
    }

}
