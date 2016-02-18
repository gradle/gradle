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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.Cast;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.CompositeBuildActionParameters;
import org.gradle.launcher.exec.CompositeBuildActionRunner;
import org.gradle.launcher.exec.CompositeBuildController;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.protocol.CompositeBuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.eclipse.SetContainer;
import org.gradle.tooling.internal.protocol.eclipse.SetOfEclipseProjects;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.connection.CompositeParameters;
import org.gradle.tooling.internal.provider.connection.GradleParticipantBuild;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    private Map<String, Class<? extends HierarchicalElement>> modelRequestTypeToModelTypeMapping = new HashMap<String, Class<? extends HierarchicalElement>>() {{
        this.put(SetOfEclipseProjects.class.getName(), EclipseProject.class);
    }};


    @Override
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        final String requestedModelName = ((BuildModelAction) action).getModelName();
        Class<? extends HierarchicalElement> modelType = modelRequestTypeToModelTypeMapping.get(requestedModelName);
        if (modelType != null) {
            ProgressLoggerFactory progressLoggerFactory = buildController.getBuildScopeServices().get(ProgressLoggerFactory.class);
            Set<Object> results = aggregateModels(modelType, actionParameters, requestContext.getCancellationToken(), progressLoggerFactory);
            SetContainer setContainer = new SetContainer(results);
            PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
            buildController.setResult(new BuildActionResult(payloadSerializer.serialize(setContainer), null));
        } else {
            throw new CompositeBuildExceptionVersion1(new IllegalArgumentException("Unknown model " + requestedModelName));
        }
    }

    private Set<Object> aggregateModels(Class<? extends HierarchicalElement> modelType, CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        Set<Object> results = new LinkedHashSet<Object>();
        final CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        results.addAll(fetchModels(compositeParameters.getBuilds(), modelType, cancellationToken, compositeParameters.getGradleUserHomeDir(), compositeParameters.getDaemonBaseDir(), compositeParameters.getDaemonMaxIdleTimeValue(), compositeParameters.getDaemonMaxIdleTimeUnits(), progressLoggerFactory));
        return results;
    }

    private <T extends HierarchicalElement> Set<T> fetchModels(List<GradleParticipantBuild> participantBuilds, Class<? extends HierarchicalElement> modelType, final BuildCancellationToken cancellationToken, File gradleUserHomeDir, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, final ProgressLoggerFactory progressLoggerFactory) {
        final Set<T> results = new LinkedHashSet<T>();
        for (GradleParticipantBuild participant : participantBuilds) {
            if (cancellationToken.isCancellationRequested()) {
                break;
            }
            ProjectConnection projectConnection = connect(participant, gradleUserHomeDir, daemonBaseDir, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
            ModelBuilder<? extends HierarchicalElement> modelBuilder = projectConnection.model(modelType);
            modelBuilder.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
            modelBuilder.addProgressListener(new ProgressListenerToProgressLoggerAdapter(progressLoggerFactory));
            if (cancellationToken.isCancellationRequested()) {
                projectConnection.close();
                break;
            }
            try {
                accumulate(results, modelBuilder.get());
            } catch (GradleConnectionException e) {
                throw new CompositeBuildExceptionVersion1(e);
            } finally {
                projectConnection.close();
            }
        }
        return results;
    }

    private <T extends HierarchicalElement> void accumulate(Set<T> allResults, HierarchicalElement element) {
        allResults.add(Cast.<T>uncheckedCast(element));
        for (HierarchicalElement child : element.getChildren().getAll()) {
            accumulate(allResults, child);
        }
    }

    private ProjectConnection connect(GradleParticipantBuild build, File gradleUserHomeDir, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        DefaultGradleConnector connector = getInternalConnector();
        if (gradleUserHomeDir != null) {
            connector.useGradleUserHomeDir(gradleUserHomeDir);
        }
        if (daemonBaseDir != null) {
            connector.daemonBaseDir(daemonBaseDir);
        }
        if (daemonMaxIdleTimeValue != null && daemonMaxIdleTimeUnits != null) {
            connector.daemonMaxIdleTime(daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
        }
        connector.searchUpwards(false);
        connector.forProjectDirectory(build.getProjectDir());
        return configureDistribution(connector, build).connect();
    }

    private DefaultGradleConnector getInternalConnector() {
        return (DefaultGradleConnector) GradleConnector.newConnector();
    }

    private GradleConnector configureDistribution(GradleConnector connector, GradleParticipantBuild build) {
        if (build.getGradleDistribution() == null) {
            if (build.getGradleHome() == null) {
                if (build.getGradleVersion() == null) {
                    connector.useBuildDistribution();
                } else {
                    connector.useGradleVersion(build.getGradleVersion());
                }
            } else {
                connector.useInstallation(build.getGradleHome());
            }
        } else {
            connector.useDistribution(build.getGradleDistribution());
        }

        return connector;
    }

    private final static class CancellationTokenAdapter implements CancellationToken, CancellationTokenInternal {
        private final BuildCancellationToken token;

        private CancellationTokenAdapter(BuildCancellationToken token) {
            this.token = token;
        }

        @Override
        public boolean isCancellationRequested() {
            return token.isCancellationRequested();
        }

        @Override
        public BuildCancellationToken getToken() {
            return token;
        }
    }

}

