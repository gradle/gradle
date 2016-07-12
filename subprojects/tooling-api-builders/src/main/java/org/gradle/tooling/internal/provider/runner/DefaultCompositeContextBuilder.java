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
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;

import java.util.List;

// TODO:DAZ Work out a way to have this registered as a BuildSession scoped service
public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final StartParameter buildStartParam;
    private final BuildRequestContext requestContext;
    private final ServiceRegistry sharedServices;
    private final boolean propagateFailures;

    public DefaultCompositeContextBuilder(StartParameter startParameter, BuildRequestContext requestContext,
                                          ServiceRegistry services, boolean propagateFailures) {
        this.buildStartParam = startParameter;
        this.requestContext = requestContext;
        this.sharedServices = services;
        this.propagateFailures = propagateFailures;
    }

    @Override
    public void addToCompositeContext(List<GradleParticipantBuild> participantBuilds) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeBuildContext context = sharedServices.get(CompositeBuildContext.class);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(sharedServices.get(StyledTextOutputFactory.class), buildStartParam, requestContext.getClient());
        CompositeContextBuildActionRunner builder = new CompositeContextBuildActionRunner(context, propagateFailures, exceptionReporter);
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, builder);

        for (GradleParticipantBuild participant : participantBuilds) {
            StartParameter participantStartParam = buildStartParam.newInstance();
            participantStartParam.setProjectDir(participant.getProjectDir());

            participantStartParam.setConfigureOnDemand(false);
            if (participantStartParam.getLogLevel() == LogLevel.LIFECYCLE) {
                participantStartParam.setLogLevel(LogLevel.QUIET);
                LOGGER.lifecycle("[composite-build] Configuring participant: " + participant.getProjectDir());
            }

            buildActionExecuter.execute(new ExecuteBuildAction(participantStartParam), requestContext, null, sharedServices);
        }
    }
}
