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

import org.gradle.api.Transformer;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.List;

public class DefaultModelBuilder<T> extends AbstractLongRunningOperation<DefaultModelBuilder<T>> implements ModelBuilder<T> {
    private final Class<T> modelType;
    private final AsyncConsumerActionExecutor connection;

    public DefaultModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        this.modelType = modelType;
        this.connection = connection;
        operationParamsBuilder.setEntryPoint("ModelBuilder API");
    }

    @Override
    protected DefaultModelBuilder<T> getThis() {
        return this;
    }

    @Override
    public T get() throws GradleConnectionException {
        BlockingResultHandler<T> handler = new BlockingResultHandler<T>(modelType);
        get(handler);
        return handler.getResult();
    }

    @Override
    public void get(final ResultHandler<? super T> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<T>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }
            @Override
            public T run(ConsumerConnection connection) {
                T model = connection.run(modelType, operationParameters);
                return model;
            }
        }, new ResultHandlerAdapter<T>(handler));
    }

    @Override
    public DefaultModelBuilder<T> forTasks(String... tasks) {
        // only set a non-null task list on the operationParamsBuilder if at least one task has been given to this method,
        // this is needed since any non-null list, even if empty, is treated as 'execute these tasks before building the model'
        // this would cause an error when fetching the BuildEnvironment model
        List<String> rationalizedTasks = rationalizeInput(tasks);
        operationParamsBuilder.setTasks(rationalizedTasks);
        return this;
    }

    @Override
    public ModelBuilder<T> forTasks(Iterable<String> tasks) {
        operationParamsBuilder.setTasks(rationalizeInput(tasks));
        return this;
    }

    private class ResultHandlerAdapter<T> extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<T> {
        public ResultHandlerAdapter(ResultHandler<? super T> handler) {
            super(handler, new ExceptionTransformer(new Transformer<String, Throwable>() {
                @Override
                public String transform(Throwable failure) {
                    String message = String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connection.getDisplayName());
                    if (!(failure instanceof UnsupportedMethodException) && failure instanceof UnsupportedOperationException) {
                        message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
                    }
                    return message;
                }
            }));
        }
    }
}
