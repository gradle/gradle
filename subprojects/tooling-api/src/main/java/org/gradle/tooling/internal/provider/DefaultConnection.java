/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.messaging.actor.Actor;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

public class DefaultConnection implements ConnectionVersion4 {
    private final ConnectionParametersVersion1 parameters;
    private final ServiceRegistry loggingServices = new LoggingServiceRegistry(false);
    private Worker worker;
    private Actor actor;

    public DefaultConnection(ConnectionParametersVersion1 parameters, ActorFactory actorFactory) {
        this.parameters = parameters;
        actor = actorFactory.createActor(new WorkerImpl(loggingServices));
        worker = actor.getProxy(Worker.class);
    }

    public String getDisplayName() {
        return "Gradle connection";
    }

    public void stop() {
        if (actor != null) {
            try {
                actor.stop();
            } finally {
                actor = null;
                worker = null;
            }
        }
    }

    public void executeBuild(BuildParametersVersion1 buildParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super Void> handler) throws IllegalStateException {
        worker.build(buildParameters, operationParameters, handler);
    }

    public void getModel(ModelFetchParametersVersion1 fetchParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super ProjectVersion3> handler) throws UnsupportedOperationException, IllegalStateException {
        worker.buildModel(fetchParameters, operationParameters, handler);
    }

    private interface Worker {
        void buildModel(ModelFetchParametersVersion1 fetchParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super ProjectVersion3> handler);

        void build(BuildParametersVersion1 buildParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super Void> handler);
    }

    private class WorkerImpl implements Worker {
        private final ServiceRegistry loggingServices;
        private final GradleLauncherFactory gradleLauncherFactory;

        public WorkerImpl(ServiceRegistry loggingServices) {
            this.loggingServices = loggingServices;
            gradleLauncherFactory = new DefaultGradleLauncherFactory(loggingServices);
            GradleLauncher.injectCustomFactory(gradleLauncherFactory);
        }

        public void buildModel(ModelFetchParametersVersion1 fetchParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super ProjectVersion3> handler) {
            try {
                handler.onComplete(buildModel(fetchParameters, operationParameters));
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        public void build(BuildParametersVersion1 buildParameters, LongRunningOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super Void> handler) {
            try {
                build(buildParameters, operationParameters);
                handler.onComplete(null);
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        private void build(BuildParametersVersion1 buildParameters, LongRunningOperationParametersVersion1 operationParameters) {
            StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);
            startParameter.setTaskNames(buildParameters.getTasks());

            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            configureLauncher(operationParameters, gradleLauncher);
            wrapAndRethrowFailure(gradleLauncher.run());
        }

        private ProjectVersion3 buildModel(ModelFetchParametersVersion1 fetchParameters, LongRunningOperationParametersVersion1 operationParameters) throws UnsupportedOperationException {
            Class<? extends ProjectVersion3> type = fetchParameters.getType();
            if (!type.isAssignableFrom(EclipseProjectVersion3.class)) {
                throw new UnsupportedOperationException(String.format("Cannot build model of type '%s'.", type.getSimpleName()));
            }

            StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);

            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            configureLauncher(operationParameters, gradleLauncher);

            boolean projectDependenciesOnly = !EclipseProjectVersion3.class.isAssignableFrom(type);
            boolean includeTasks = BuildableProjectVersion1.class.isAssignableFrom(type);

            ModelBuildingAdapter adapter = new ModelBuildingAdapter(
                    new EclipsePluginApplier(), new ModelBuilder(includeTasks, projectDependenciesOnly));
            gradleLauncher.addListener(adapter);

            wrapAndRethrowFailure(gradleLauncher.getBuildAnalysis());
            return type.cast(adapter.getProject());
        }

        private void configureLauncher(final LongRunningOperationParametersVersion1 operationParameters, GradleLauncher gradleLauncher) {
            if (operationParameters.getStandardOutput() != null) {
                gradleLauncher.addStandardOutputListener(new StreamBackedStandardOutputListener(operationParameters.getStandardOutput()));
            }
            if (operationParameters.getStandardError() != null) {
                gradleLauncher.addStandardErrorListener(new StreamBackedStandardOutputListener(operationParameters.getStandardError()));
            }
            loggingServices.get(LoggingOutputInternal.class).addOutputEventListener(new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    if (event instanceof ProgressStartEvent) {
                        ProgressStartEvent startEvent = (ProgressStartEvent) event;
                        operationParameters.getProgressListener().statusChanged(startEvent.getDescription());
                    }
                }
            });
        }

        private void wrapAndRethrowFailure(BuildResult result) {
            if (result.getFailure() != null) {
                throw new BuildExceptionVersion1(result.getFailure());
            }
        }
    }
}
