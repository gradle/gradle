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

import org.gradle.StartParameter;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.GradleBuildController;

public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final StartParameter buildStartParam;
    private final ServiceRegistry sharedServices;

    public DefaultCompositeContextBuilder(StartParameter startParameter, ServiceRegistry services) {
        this.buildStartParam = startParameter;
        this.sharedServices = services;
    }

    @Override
    public void addToCompositeContext(Iterable<IncludedBuild> includedBuilds) {
        doAddToCompositeContext(includedBuilds);
    }

    private void doAddToCompositeContext(Iterable<IncludedBuild> includedBuilds) {
        CompositeBuildContext context = sharedServices.get(CompositeBuildContext.class);

        for (IncludedBuild build : includedBuilds) {
            IncludedBuildInternal buildInternal = (IncludedBuildInternal) build;
            StartParameter includedBuildStartParam = buildStartParam.newBuild();
            includedBuildStartParam.setProjectDir(build.getProjectDir());
            includedBuildStartParam.setSearchUpwards(false);
            includedBuildStartParam.setConfigureOnDemand(false);

            DependencySubstitutionsInternal substitutions = ((IncludedBuildInternal) build).resolveDependencySubstitutions();
            if (!substitutions.hasRules()) {
                configureBuildToDetermineSubstitutions(buildInternal, context, includedBuildStartParam);
            } else {
                context.registerSubstitution(substitutions.getRuleAction());
            }

            configureBuildToRegisterDependencyMetadata(buildInternal, context, includedBuildStartParam);
        }
    }

    private void configureBuildToDetermineSubstitutions(IncludedBuildInternal build, CompositeBuildContext context, StartParameter includedBuildStartParam) {
        LOGGER.lifecycle("[composite-build] Configuring build: " + includedBuildStartParam.getProjectDir());
        CompositeSubstitutionsActionRunner contextBuilder = new CompositeSubstitutionsActionRunner(context);
        GradleLauncher gradleLauncher = build.createGradleLauncher();
        try {
            contextBuilder.run(new GradleBuildController(gradleLauncher));
        } finally {
            gradleLauncher.stop();
        }
    }

    private void configureBuildToRegisterDependencyMetadata(IncludedBuildInternal build, CompositeBuildContext context, StartParameter includedBuildStartParam) {
        CompositeContextBuildActionRunner contextBuilder = new CompositeContextBuildActionRunner(context);
        GradleLauncher gradleLauncher = build.createGradleLauncher();
        try {
            contextBuilder.run(new GradleBuildController(gradleLauncher));
        } finally {
            gradleLauncher.stop();
        }
    }
}
