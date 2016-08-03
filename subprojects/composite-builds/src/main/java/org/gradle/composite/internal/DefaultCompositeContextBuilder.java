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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.composite.CompositeContextBuildActionRunner;
import org.gradle.api.internal.composite.CompositeSubstitutionsActionRunner;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.composite.IncludedBuild;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.GradleBuildController;

import java.util.Map;

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

            if (build.getProvided().isEmpty()) {
                configureBuildToDetermineSubstitutions(requestContext, gradleLauncherFactory, context, includedBuildStartParam);
            } else {
                registerDefinedSubstitutions(context, build);
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

    private void registerDefinedSubstitutions(CompositeBuildContext context, IncludedBuild build) {
        // Register additional provided components
        for (Map.Entry<String, String> entry : build.getProvided().entrySet()) {
            String[] parts = entry.getKey().split(":");
            ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(parts[0], parts[1], parts[2]);
            ProjectComponentIdentifier projectComponentIdentifier = DefaultProjectComponentIdentifier.newId(entry.getValue());
            context.registerSubstitution(moduleVersionIdentifier, projectComponentIdentifier);
        }
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
