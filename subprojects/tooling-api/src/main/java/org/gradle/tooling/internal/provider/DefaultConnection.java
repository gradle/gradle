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

import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.messaging.actor.Actor;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

public class DefaultConnection implements ConnectionVersion3 {
    private final ActorFactory actorFactory;
    private final ConnectionParametersVersion1 parameters;
    private Worker worker;
    private Actor actor;

    public DefaultConnection(ConnectionParametersVersion1 parameters, ActorFactory actorFactory) {
        this.parameters = parameters;
        this.actorFactory = actorFactory;
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
            }
        }
    }

    public <T extends ProjectVersion3> void getModel(Class<T> type, ResultHandlerVersion1<? super T> handler) throws UnsupportedOperationException {
        if (worker == null) {
            actor = actorFactory.createActor(new WorkerImpl());
            worker = actor.getProxy(Worker.class);
        }
        worker.build(type, handler);
    }

    private interface Worker {
        <T extends ProjectVersion3> void build(Class<T> type, ResultHandlerVersion1<? super T> handler);
    }

    private class WorkerImpl implements Worker {
        public <T extends ProjectVersion3> void build(Class<T> type, ResultHandlerVersion1<? super T> handler) {
            try {
                handler.onComplete(build(type));
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        private <T extends ProjectVersion3> T build(Class<T> type) throws UnsupportedOperationException {
            if (type.isAssignableFrom(EclipseProjectVersion3.class)) {
                StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);
                startParameter.setTaskNames(asList(":eclipseConfigurer"));

                GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);

                ModelBuildingAdapter adapter = new ModelBuildingAdapter();
                gradleLauncher.addListener(adapter);

                boolean fullModel = EclipseProjectVersion3.class.isAssignableFrom(type);
                boolean includeTasks = BuildableProjectVersion1.class.isAssignableFrom(type);
                if (fullModel) {
                    adapter.setConfigurer(new EclipsePluginApplier());
                } else {
                    adapter.setConfigurer(new MinimalModelConfigurer());
                }
                adapter.setBuilder(new ModelBuilder(includeTasks));

                gradleLauncher.run().rethrowFailure();
                return type.cast(adapter.getProject());
            }

            throw new UnsupportedOperationException();
        }
    }
}
