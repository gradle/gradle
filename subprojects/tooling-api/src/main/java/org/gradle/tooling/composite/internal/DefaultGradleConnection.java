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

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.composite.ModelResult;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.model.eclipse.EclipseProject;

public class DefaultGradleConnection implements GradleConnectionInternal {
    private final AsyncConsumerActionExecutor asyncConnection;
    private final CompositeConnectionParameters parameters;

    DefaultGradleConnection(AsyncConsumerActionExecutor asyncConnection, CompositeConnectionParameters parameters) {
        this.asyncConnection = asyncConnection;
        this.parameters = parameters;
    }

    @Override
    public <T> Iterable<ModelResult<T>> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return models(modelType).get();
    }

    @Override
    public <T> void getModels(Class<T> modelType, ResultHandler<? super Iterable<ModelResult<T>>> handler) throws IllegalStateException {
        models(modelType).get(handler);
    }

    @Override
    public <T> ModelBuilder<Iterable<ModelResult<T>>> models(Class<T> modelType) {
        checkSupportedModelType(modelType);
        return new ModelResultCompositeModelBuilder<T>(new DefaultCompositeModelBuilder<T>(modelType, asyncConnection, parameters));
    }

    private <T> void checkSupportedModelType(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }

        // TODO: Remove
        if (!modelType.equals(EclipseProject.class)) {
            throw new UnsupportedOperationException(String.format("The only supported model for a Gradle composite is %s.class.", EclipseProject.class.getSimpleName()));
        }
    }

    @Override
    public void close() {
        asyncConnection.stop();
    }
}
