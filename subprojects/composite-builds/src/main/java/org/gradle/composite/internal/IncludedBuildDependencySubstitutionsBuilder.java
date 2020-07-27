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
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncludedBuildDependencySubstitutionsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncludedBuildDependencySubstitutionsBuilder.class);

    private final CompositeBuildContext context;
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, Capability> capabilitiesParser;

    public IncludedBuildDependencySubstitutionsBuilder(CompositeBuildContext context,
                                                       Instantiator instantiator,
                                                       ObjectFactory objectFactory,
                                                       ImmutableAttributesFactory attributesFactory,
                                                       NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                                       NotationParser<Object, Capability> capabilitiesParser) {
        this.context = context;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.capabilitiesParser = capabilitiesParser;
    }

    public void build(IncludedBuildState build) {
        DependencySubstitutionsInternal substitutions = resolveDependencySubstitutions(build);
        if (!substitutions.hasRules()) {
            // Configure the included build to discover available modules
            LOGGER.info("[composite-build] Configuring build: " + build.getRootDirectory());
            context.addAvailableModules(build.getAvailableModules());
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }

    private DependencySubstitutionsInternal resolveDependencySubstitutions(IncludedBuildState build) {
        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forIncludedBuild(build, instantiator, objectFactory, attributesFactory, moduleSelectorNotationParser, capabilitiesParser);
        build.getRegisteredDependencySubstitutions().execute(dependencySubstitutions);
        return dependencySubstitutions;
    }
}
