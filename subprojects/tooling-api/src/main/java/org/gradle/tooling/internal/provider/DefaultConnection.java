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
import org.gradle.logging.internal.StreamBackedStandardOutputListener;
import org.gradle.messaging.actor.Actor;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

public class DefaultConnection implements ConnectionVersion4 {
    private final ConnectionParametersVersion1 parameters;
    private Worker worker;
    private Actor actor;

    public DefaultConnection(ConnectionParametersVersion1 parameters, ActorFactory actorFactory) {
        this.parameters = parameters;
        actor = actorFactory.createActor(new WorkerImpl());
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

    public <T extends ProjectVersion3> void getModel(Class<T> type, ModelFetchParametersVersion1 fetchParameters, ResultHandlerVersion1<? super T> handler) throws UnsupportedOperationException, IllegalStateException {
        worker.buildModel(type, fetchParameters, handler);
    }

    public void executeBuild(BuildParametersVersion1 buildParameters, ResultHandlerVersion1<? super Void> handler) throws IllegalStateException {
        worker.build(buildParameters, handler);
    }

    private interface Worker {
        <T extends ProjectVersion3> void buildModel(Class<T> type, ModelFetchParametersVersion1 fetchParameters, ResultHandlerVersion1<? super T> handler);

        void build(BuildParametersVersion1 buildParameters, ResultHandlerVersion1<? super Void> handler);
    }

    private class WorkerImpl implements Worker {
        public <T extends ProjectVersion3> void buildModel(Class<T> type, ModelFetchParametersVersion1 fetchParameters, ResultHandlerVersion1<? super T> handler) {
            try {
                handler.onComplete(buildModel(type, fetchParameters));
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        public void build(BuildParametersVersion1 buildParameters, ResultHandlerVersion1<? super Void> handler) {
            try {
                build(buildParameters);
                handler.onComplete(null);
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        private void build(BuildParametersVersion1 buildParameters) {
            StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);
            startParameter.setTaskNames(buildParameters.getTasks());

            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            configureLauncher(buildParameters, gradleLauncher);
            wrapAndRethrowFailure(gradleLauncher.run());
        }

        private <T extends ProjectVersion3> T buildModel(Class<T> type, ModelFetchParametersVersion1 fetchParameters) throws UnsupportedOperationException {
            if (!type.isAssignableFrom(EclipseProjectVersion3.class)) {
                throw new UnsupportedOperationException(String.format("Cannot build model of type '%s'.", type.getSimpleName()));
            }

            StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);

            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            configureLauncher(fetchParameters, gradleLauncher);

            boolean projectDependenciesOnly = !EclipseProjectVersion3.class.isAssignableFrom(type);
            boolean includeTasks = BuildableProjectVersion1.class.isAssignableFrom(type);

            ModelBuildingAdapter adapter = new ModelBuildingAdapter(
                    new EclipsePluginApplier(), new ModelBuilder(includeTasks, projectDependenciesOnly));
            gradleLauncher.addListener(adapter);

            wrapAndRethrowFailure(gradleLauncher.getBuildAnalysis());
            return type.cast(adapter.getProject());
        }

        private void configureLauncher(BuildOperationVersion1 buildParameters, GradleLauncher gradleLauncher) {
            if (buildParameters.getStandardOutput() != null) {
                gradleLauncher.addStandardOutputListener(new StreamBackedStandardOutputListener(buildParameters.getStandardOutput()));
            }
            if (buildParameters.getStandardError() != null) {
                gradleLauncher.addStandardErrorListener(new StreamBackedStandardOutputListener(buildParameters.getStandardError()));
            }
        }

        private void wrapAndRethrowFailure(BuildResult result) {
            if (result.getFailure() != null) {
                throw new BuildExceptionVersion1(result.getFailure());
            }
        }
    }
}
