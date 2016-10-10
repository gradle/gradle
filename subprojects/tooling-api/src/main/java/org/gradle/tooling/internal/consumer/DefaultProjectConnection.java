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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.model.ModelResults;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;

class DefaultProjectConnection implements ProjectConnection {
    private final AsyncConsumerActionExecutor connection;
    private final ConnectionParameters parameters;

    public DefaultProjectConnection(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        this.connection = connection;
        this.parameters = parameters;
    }

    public void close() {
        connection.stop();
    }

    public <T> T getModel(Class<T> modelType) {
        return model(modelType).get();
    }

    public <T> void getModel(final Class<T> modelType, final ResultHandler<? super T> handler) {
        model(modelType).get(handler);
    }

    @Override
    public <T> ModelResults<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return models(modelType).get();
    }

    @Override
    public <T> void getModels(Class<T> modelType, ResultHandler<? super ModelResults<T>> handler) throws IllegalStateException {
        models(modelType).get(handler);
    }

    @Override
    public <T> ModelBuilder<ModelResults<T>> models(Class<T> modelType) {
        checkSupportedModelType(modelType);
        return new DefaultMultiModelBuilder<T>(modelType, connection, parameters);
    }

    public BuildLauncher newBuild() {
        return new ProjectConnectionBuildLauncher(connection, parameters);
    }

    @Override
    public TestLauncher newTestLauncher() {
        return new DefaultTestLauncher(connection, parameters);
    }

    public <T> ModelBuilder<T> model(Class<T> modelType) {
        checkSupportedModelType(modelType);
        return new DefaultModelBuilder<T>(modelType, connection, parameters);
    }

    private <T> void checkSupportedModelType(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }
    }

    public <T> BuildActionExecuter<T> action(final BuildAction<T> buildAction) {
        return new DefaultBuildActionExecuter<T>(buildAction, connection, parameters);
    }

}
