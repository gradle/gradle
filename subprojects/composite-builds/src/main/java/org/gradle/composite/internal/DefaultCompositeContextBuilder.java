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
import org.gradle.api.internal.composite.CompositeContextBuildActionRunner;
import org.gradle.api.internal.composite.CompositeSubstitutionsActionRunner;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
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
        doAddToCompositeContext(includedBuilds, null);
    }

    @Override
    public void addToCompositeContext(Iterable<IncludedBuild> includedBuilds, BuildRequestContext requestContext) {
        doAddToCompositeContext(includedBuilds, requestContext);
    }

    private void doAddToCompositeContext(Iterable<IncludedBuild> includedBuilds, BuildRequestContext requestContext) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeBuildContext context = sharedServices.get(CompositeBuildContext.class);

        for (IncludedBuild build : includedBuilds) {
            StartParameter includedBuildStartParam = buildStartParam.newBuild();
            includedBuildStartParam.setProjectDir(build.getProjectDir());
            includedBuildStartParam.setSearchUpwards(false);
            includedBuildStartParam.setConfigureOnDemand(false);

            DependencySubstitutionsInternal substitutions = ((IncludedBuildInternal) build).getDependencySubstitution();
            if (!substitutions.hasDependencySubstitutionRules()) {
                configureBuildToDetermineSubstitutions(requestContext, gradleLauncherFactory, context, includedBuildStartParam);
            } else {
                context.registerSubstitution(substitutions.getDependencySubstitutionRule());
            }

            configureBuildToRegisterDependencyMetadata(requestContext, gradleLauncherFactory, context, includedBuildStartParam);
        }
    }

    private void configureBuildToDetermineSubstitutions(BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory, CompositeBuildContext context, StartParameter includedBuildStartParam) {
        GradleLauncher gradleLauncher = createGradleLauncher(includedBuildStartParam, requestContext, gradleLauncherFactory);
        LOGGER.lifecycle("[composite-build] Configuring build: " + includedBuildStartParam.getProjectDir());
        CompositeSubstitutionsActionRunner contextBuilder = new CompositeSubstitutionsActionRunner(context);
        contextBuilder.run(new GradleBuildController(gradleLauncher));
    }

    private void configureBuildToRegisterDependencyMetadata(BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory, CompositeBuildContext context, StartParameter includedBuildStartParam) {
        GradleLauncher gradleLauncher = createGradleLauncher(includedBuildStartParam, requestContext, gradleLauncherFactory);
        CompositeContextBuildActionRunner contextBuilder = new CompositeContextBuildActionRunner(context);
        contextBuilder.run(new GradleBuildController(gradleLauncher));
    }

    private GradleLauncher createGradleLauncher(StartParameter participantStartParam, BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory) {
        if (requestContext == null) {
            return gradleLauncherFactory.nestedInstance(participantStartParam, sharedServices);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(participantStartParam, requestContext, sharedServices);
        gradleLauncher.addStandardOutputListener(requestContext.getOutputListener());
        gradleLauncher.addStandardErrorListener(requestContext.getErrorListener());
        return gradleLauncher;
    }
}
