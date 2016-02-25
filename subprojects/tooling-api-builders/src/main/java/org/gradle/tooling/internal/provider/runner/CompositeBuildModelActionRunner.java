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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.composite.*;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.protocol.CompositeBuildExceptionVersion1;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.HierarchicalElement;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        Class<? extends HierarchicalElement> modelType = resolveModelType((BuildModelAction) action);
        ProgressLoggerFactory progressLoggerFactory = buildController.getBuildScopeServices().get(ProgressLoggerFactory.class);
        Set<Object> results = aggregateModels(modelType, actionParameters, requestContext.getCancellationToken(), progressLoggerFactory);
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private Class<? extends HierarchicalElement> resolveModelType(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        Class<? extends HierarchicalElement> modelType;
        try {
            modelType = Cast.uncheckedCast(HierarchicalElement.class.getClassLoader().loadClass(requestedModelName));
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        if (!HierarchicalElement.class.isAssignableFrom(modelType)) {
            throw new CompositeBuildExceptionVersion1(new UnsupportedOperationException("Unsupported model type " + requestedModelName + ". Must be subtype of HierarchicalElement."));
        }
        return modelType;
    }

    private Set<Object> aggregateModels(Class<? extends HierarchicalElement> modelType, CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        Set<Object> results = new LinkedHashSet<Object>();
        final CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        results.addAll(fetchModels(compositeParameters.getBuilds(), modelType, cancellationToken, compositeParameters, progressLoggerFactory));
        return results;
    }

    private <T extends HierarchicalElement> Set<T> fetchModels(List<GradleParticipantBuild> participantBuilds, Class<? extends HierarchicalElement> modelType, final BuildCancellationToken cancellationToken, CompositeParameters compositeParameters, final ProgressLoggerFactory progressLoggerFactory) {
        final Set<T> results = new LinkedHashSet<T>();
        for (GradleParticipantBuild participant : participantBuilds) {
            if (cancellationToken.isCancellationRequested()) {
                break;
            }
            ProjectConnection projectConnection = connect(participant, compositeParameters);
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

    private ProjectConnection connect(GradleParticipantBuild build, CompositeParameters compositeParameters) {
        DefaultGradleConnector connector = getInternalConnector();
        File gradleUserHomeDir = compositeParameters.getGradleUserHomeDir();
        File daemonBaseDir = compositeParameters.getDaemonBaseDir();
        Integer daemonMaxIdleTimeValue = compositeParameters.getDaemonMaxIdleTimeValue();
        TimeUnit daemonMaxIdleTimeUnits = compositeParameters.getDaemonMaxIdleTimeUnits();
        Boolean embeddedParticipants = compositeParameters.isEmbeddedParticipants();

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

        if (embeddedParticipants) {
            connector.embedded(true);
            connector.useClasspathDistribution();
            return connector.connect();
        } else {
            return configureDistribution(connector, build).connect();
        }
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

        public boolean isCancellationRequested() {
            return token.isCancellationRequested();
        }

        public BuildCancellationToken getToken() {
            return token;
        }
    }

}

