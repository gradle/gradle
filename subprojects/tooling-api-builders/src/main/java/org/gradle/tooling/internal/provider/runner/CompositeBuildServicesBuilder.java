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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.StartParameter;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.composite.CompositeContextBuildActionRunner;
import org.gradle.api.internal.composite.CompositeScopeServices;
import org.gradle.api.internal.composite.DefaultBuildableCompositeBuildContext;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.composite.CompositeParameters;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;

class CompositeBuildServicesBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(CompositeBuildServicesBuilder.class);

    public DefaultServiceRegistry createCompositeAwareServices(StartParameter buildStartParameter, boolean propagateFailures,
                                                                BuildRequestContext buildRequestContext, CompositeParameters compositeParameters, ServiceRegistry sharedServices) {
        CompositeBuildContext context = constructCompositeContext(buildStartParameter, buildRequestContext, compositeParameters, sharedServices, propagateFailures);

        DefaultServiceRegistry compositeServices = (DefaultServiceRegistry) ServiceRegistryBuilder.builder()
            .displayName("Composite services")
            .parent(sharedServices)
            .build();
        compositeServices.add(CompositeBuildContext.class, context);
        compositeServices.addProvider(new CompositeScopeServices(buildStartParameter, compositeServices));
        return compositeServices;
    }

    private CompositeBuildContext constructCompositeContext(StartParameter actionStartParameter, BuildRequestContext buildRequestContext,
                                                            CompositeParameters compositeParameters, ServiceRegistry sharedServices, boolean propagateFailures) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(sharedServices.get(StyledTextOutputFactory.class), actionStartParameter, buildRequestContext.getClient());
        CompositeBuildContext context = new DefaultBuildableCompositeBuildContext();
        CompositeContextBuildActionRunner builder = new CompositeContextBuildActionRunner(context, propagateFailures, exceptionReporter);
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, builder);

        for (GradleParticipantBuild participant : compositeParameters.getBuilds()) {
            StartParameter startParameter = actionStartParameter.newInstance();
            startParameter.setProjectDir(participant.getProjectDir());

            startParameter.setConfigureOnDemand(false);
            if (startParameter.getLogLevel() == LogLevel.LIFECYCLE) {
                startParameter.setLogLevel(LogLevel.QUIET);
                LOGGER.lifecycle("[composite-build] Configuring participant: " + participant.getProjectDir());
            }

            buildActionExecuter.execute(new ExecuteBuildAction(startParameter), buildRequestContext, null, sharedServices);
        }
        return context;
    }

}
