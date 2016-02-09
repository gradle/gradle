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

import com.google.common.collect.Sets;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.CompositeBuildActionParameters;
import org.gradle.launcher.exec.CompositeBuildActionRunner;
import org.gradle.launcher.exec.CompositeBuildController;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.GradleCompositeException;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
            Set<Object> results = aggregateModels(modelType, actionParameters, requestContext.getCancellationToken());
            SetContainer setContainer = new SetContainer(results);
            PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
            buildController.setResult(new BuildActionResult(payloadSerializer.serialize(setContainer), null));
        } else {
            throw new GradleCompositeException("Unknown model " + requestedModelName);
        }
    }

    private Set<Object> aggregateModels(Class<? extends HierarchicalElement> modelType, CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken) {
        Set<Object> results = new LinkedHashSet<Object>();
        final CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        results.addAll(fetchModels(compositeParameters.getBuilds(), modelType, cancellationToken, compositeParameters.getDaemonBaseDir(), compositeParameters.getDaemonMaxIdleTimeValue(), compositeParameters.getDaemonMaxIdleTimeUnits()));
        return results;
    }

    private <T extends HierarchicalElement> Set<T> fetchModels(List<GradleParticipantBuild> participantBuilds, Class<T> modelType, final BuildCancellationToken cancellationToken, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        final Set<T> results = Sets.newConcurrentHashSet();
        final AtomicReference<Throwable> firstFailure = new AtomicReference<Throwable>();
        final CountDownLatch countDownLatch = new CountDownLatch(participantBuilds.size());
        for (GradleParticipantBuild participant : participantBuilds) {
            ProjectConnection projectConnection = connect(participant, daemonBaseDir, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
            ModelBuilder<T> modelBuilder = projectConnection.model(modelType);
            if (cancellationToken != null) {
                modelBuilder.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
            }
            modelBuilder.get(new MultiResultHandler<T>(projectConnection, countDownLatch, firstFailure, new HierarchicalResultAdapter<T>(results)));
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            UncheckedException.throwAsUncheckedException(e);
        }
        if (firstFailure.get() != null) {
            throw new GradleCompositeException("Error retrieving model", firstFailure.get());
        }
        return new HashSet<T>(results);
    }

    private ProjectConnection connect(GradleParticipantBuild build, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        DefaultGradleConnector connector = getInternalConnector();
        if (daemonBaseDir != null) {
            connector.daemonBaseDir(daemonBaseDir);
        }
        connector.daemonMaxIdleTime(daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
        connector.searchUpwards(false);
        connector.forProjectDirectory(build.getProjectDir());
        return configureDistribution(connector, build).connect();
    }

    private DefaultGradleConnector getInternalConnector() {
        return (DefaultGradleConnector)GradleConnector.newConnector();
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

    private final static class MultiResultHandler<T> implements ResultHandler<T> {
        private final ProjectConnection projectConnection;
        private final CountDownLatch countDownLatch;
        private final AtomicReference<Throwable> firstFailure;
        private final ResultHandler<T> delegate;

        private MultiResultHandler(ProjectConnection projectConnection, CountDownLatch countDownLatch, AtomicReference<Throwable> firstFailure, ResultHandler<T> delegate) {
            this.projectConnection = projectConnection;
            this.countDownLatch = countDownLatch;
            this.firstFailure = firstFailure;
            this.delegate = delegate;
        }

        @Override
        public void onComplete(T result) {
            try {
                delegate.onComplete(result);
            } finally {
                finishUsage();
            }
        }

        private void finishUsage() {
            try {
                projectConnection.close();
            } finally {
                countDownLatch.countDown();
            }
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            try {
                firstFailure.compareAndSet(null, failure);
                delegate.onFailure(failure);
            } finally {
                finishUsage();
            }
        }
    }

    private static class HierarchicalResultAdapter<T extends HierarchicalElement> implements ResultHandler<T> {
        private final Set<T> allResults;

        private HierarchicalResultAdapter(Set<T> allResults) {
            this.allResults = allResults;
        }

        public void onComplete(T result) {
            accumulate(result);
        }

        @Override
        public void onFailure(GradleConnectionException failure) {

        }

        private void accumulate(HierarchicalElement element) {
            allResults.add(Cast.<T>uncheckedCast(element));
            for (HierarchicalElement child : element.getChildren().getAll()) {
                accumulate(child);
            }
        }
    }
}
