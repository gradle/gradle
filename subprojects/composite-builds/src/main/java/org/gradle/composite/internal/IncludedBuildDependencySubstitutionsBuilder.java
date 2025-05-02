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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.GlobalDependencySubstitutionRegistry;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.HashSet;
import java.util.Set;

public class IncludedBuildDependencySubstitutionsBuilder implements GlobalDependencySubstitutionRegistry {
    private final CompositeBuildContext context;
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final AttributesFactory attributesFactory;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, Capability> capabilitiesParser;
    private final Set<IncludedBuildState> processed = new HashSet<>();

    public IncludedBuildDependencySubstitutionsBuilder(
        CompositeBuildContext context,
        Instantiator instantiator,
        ObjectFactory objectFactory,
        AttributesFactory attributesFactory,
        NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
        NotationParser<Object, Capability> capabilitiesParser
    ) {
        this.context = context;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.capabilitiesParser = capabilitiesParser;
    }

    @Override
    public void registerSubstitutionsFor(CompositeBuildParticipantBuildState build) {
        if (build instanceof IncludedBuildState) {
            build((IncludedBuildState) build);
        } else if (build instanceof RootBuildState) {
            build((RootBuildState)build);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private void build(IncludedBuildState build) {
        if (processed.contains(build)) {
            // This may happen during early resolution, where we iterate through all builds to find only
            // the ones for which we need to register substitutions early so that they are available
            // during plugin application from plugin builds.
            // See: DefaultIncludedBuildRegistry.ensureConfigured()
            return;
        }
        processed.add(build);
        DependencySubstitutionsInternal substitutions = resolveDependencySubstitutions(build);
        if (!substitutions.rulesMayAddProjectDependency()) {
            context.addAvailableModules(build.getAvailableModules());
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }

    private void build(RootBuildState build) {
        context.addAvailableModules(build.getAvailableModules());
    }

    private DependencySubstitutionsInternal resolveDependencySubstitutions(IncludedBuildState build) {
        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forIncludedBuild(build, instantiator, objectFactory, attributesFactory, moduleSelectorNotationParser, capabilitiesParser);
        build.getRegisteredDependencySubstitutions().execute(dependencySubstitutions);
        return dependencySubstitutions;
    }
}
