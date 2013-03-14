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

import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.async.AsyncConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.converters.ConsumerPropertyHandler;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class DefaultModelBuilder<T extends Model, P> implements ModelBuilder<T> {
    private final Class<T> modelType;
    private final Class<P> protocolType;
    private final AsyncConnection connection;
    private final ProtocolToModelAdapter adapter;
    private ConsumerOperationParameters operationParameters;

    public DefaultModelBuilder(Class<T> modelType, Class<P> protocolType, AsyncConnection connection, ProtocolToModelAdapter adapter, ConnectionParameters parameters) {
        operationParameters = new ConsumerOperationParameters(parameters);
        this.modelType = modelType;
        this.protocolType = protocolType;
        this.connection = connection;
        this.adapter = adapter;
    }

    public T get() throws GradleConnectionException {
        BlockingResultHandler<T> handler = new BlockingResultHandler<T>(modelType);
        get(handler);
        return handler.getResult();
    }

    public void get(final ResultHandler<? super T> handler) throws IllegalStateException {
        ResultHandler<P> adaptingHandler = new ProtocolToModelAdaptingHandler(handler);
        connection.run(protocolType, operationParameters, new ResultHandlerAdapter<P>(adaptingHandler) {
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                String message = String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connection.getDisplayName());
                if (!(failure instanceof UnsupportedMethodException)
                        && failure instanceof UnsupportedOperationException) {
                    message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
                }
                return message;
            }
        });
    }

    public DefaultModelBuilder<T, P> withArguments(String... arguments) {
        operationParameters.setArguments(arguments);
        return this;
    }

    public DefaultModelBuilder<T, P> setStandardOutput(OutputStream outputStream) {
        operationParameters.setStandardOutput(outputStream);
        return this;
    }

    public DefaultModelBuilder<T, P> setStandardError(OutputStream outputStream) {
        operationParameters.setStandardError(outputStream);
        return this;
    }

    public DefaultModelBuilder<T, P> setStandardInput(InputStream inputStream) {
        operationParameters.setStandardInput(inputStream);
        return this;
    }

    public DefaultModelBuilder<T, P> setJavaHome(File javaHome) {
        operationParameters.setJavaHome(javaHome);
        return this;
    }

    public DefaultModelBuilder<T, P> setJvmArguments(String... jvmArguments) {
        operationParameters.setJvmArguments(jvmArguments);
        return this;
    }

    public DefaultModelBuilder<T, P> addProgressListener(ProgressListener listener) {
        operationParameters.addProgressListener(listener);
        return this;
    }

    public DefaultModelBuilder<T, P> forTasks(String... tasks) {
        operationParameters.setTasks(Arrays.asList(tasks));
        return this;
    }

    private class ProtocolToModelAdaptingHandler implements ResultHandler<P> {
        private final ResultHandler<? super T> handler;

        public ProtocolToModelAdaptingHandler(ResultHandler<? super T> handler) {
            this.handler = handler;
        }

        public void onComplete(P result) {
            handler.onComplete(adapter.adapt(modelType, result, new ConsumerPropertyHandler(connection.getVersionDetails())));
        }

        public void onFailure(GradleConnectionException failure) {
            handler.onFailure(failure);
        }
    }
}
