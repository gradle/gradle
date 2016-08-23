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

import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.logging.Logging;
import org.gradle.internal.composite.CompositeContextBuilder;

public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final DefaultIncludedBuilds allIncludedBuilds;
    private final CompositeBuildContext context;

    public DefaultCompositeContextBuilder(DefaultIncludedBuilds allIncludedBuilds, CompositeBuildContext context) {
        this.allIncludedBuilds = allIncludedBuilds;
        this.context = context;
    }

    @Override
    public void addToCompositeContext(Iterable<IncludedBuild> includedBuilds) {
        IncludedBuildDependencySubstitutionsBuilder contextBuilder = new IncludedBuildDependencySubstitutionsBuilder(context);
        for (IncludedBuild includedBuild : includedBuilds) {
            allIncludedBuilds.registerBuild(includedBuild);
            doAddToCompositeContext((IncludedBuildInternal) includedBuild, contextBuilder);
        }
    }

    private void doAddToCompositeContext(IncludedBuildInternal build, IncludedBuildDependencySubstitutionsBuilder contextBuilder) {
        DependencySubstitutionsInternal substitutions = build.resolveDependencySubstitutions();
        if (!substitutions.hasRules()) {
            // Configure the included build to discover substitutions
            LOGGER.lifecycle("[composite-build] Configuring build: " + build.getProjectDir());
            contextBuilder.build(build);
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }
}
