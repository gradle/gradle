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

package org.gradle.tooling.internal.connection;

import org.gradle.api.Transformer;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.connection.FailedModelResult;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.connection.ModelResults;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.consumer.BlockingResultHandler;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.ExceptionTransformer;
import org.gradle.tooling.internal.consumer.ModelExceptionTransformer;
import org.gradle.tooling.internal.consumer.ResultHandlerAdapter;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.Arrays;
import java.util.Iterator;

public class DefaultMultiModelBuilder<T> extends AbstractLongRunningOperation<DefaultMultiModelBuilder<T>> implements ModelBuilder<ModelResults<T>> {
    private final Class<T> modelType;
    private final AsyncConsumerActionExecutor connection;

    protected DefaultMultiModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        this.modelType = modelType;
        this.connection = connection;
        operationParamsBuilder.setEntryPoint("MultiModelBuilder API");
        operationParamsBuilder.setRootDirectory(parameters.getProjectDir());
    }

    @Override
    protected DefaultMultiModelBuilder<T> getThis() {
        return this;
    }

    public DefaultMultiModelBuilder<T> forTasks(String... tasks) {
        return forTasks(Arrays.asList(tasks));
    }

    @Override
    public DefaultMultiModelBuilder<T> forTasks(Iterable<String> tasks) {
        operationParamsBuilder.setTasks(rationalizeInput(tasks));
        return this;
    }

    @Override
    public ModelResults<T> get() throws GradleConnectionException, IllegalStateException {
        BlockingResultHandler<ModelResults> handler = new BlockingResultHandler<ModelResults>(ModelResults.class);
        get(handler);
        return handler.getResult();
    }

    @Override
    public void get(final ResultHandler<? super ModelResults<T>> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        final ExceptionTransformer exceptionTransformer = getModelExceptionTransformer();
        connection.run(new ConsumerAction<ModelResults<T>>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            public ModelResults<T> run(ConsumerConnection connection) {
                final Iterable<ModelResult<T>> models = connection.buildModels(modelType, operationParameters);
                return new ExceptionTransformingModelResults<T>(models, exceptionTransformer);
            }
        }, new ResultHandlerAdapter<ModelResults<T>>(handler, exceptionTransformer));
    }

    private ModelExceptionTransformer getModelExceptionTransformer() {
        return new ModelExceptionTransformer(modelType, new Transformer<String, Throwable>() {
            @Override
            public String transform(Throwable failure) {
                String message = String.format("Could not fetch models of type '%s' using %s.", modelType.getSimpleName(), connection.getDisplayName());
                if (!(failure instanceof UnsupportedMethodException) && failure instanceof UnsupportedOperationException) {
                    message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
                }
                return message;
            }
        });
    }

    private static class ExceptionTransformingModelResults<T> implements ModelResults<T> {
        private final Iterable<ModelResult<T>> models;
        private final ExceptionTransformer transformer;

        public ExceptionTransformingModelResults(Iterable<ModelResult<T>> models, ExceptionTransformer transformer) {
            this.models = models;
            this.transformer = transformer;
        }

        @Override
        public Iterator<ModelResult<T>> iterator() {
            final Iterator<ModelResult<T>> original = models.iterator();
            return new Iterator<ModelResult<T>>() {
                @Override
                public boolean hasNext() {
                    return original.hasNext();
                }

                @Override
                public ModelResult<T> next() {
                    ModelResult<T> next = original.next();
                    if (next instanceof FailedModelResult) {
                        DefaultFailedModelResult<T> failedResult = (DefaultFailedModelResult<T>) next;
                        GradleConnectionException transformedFailure = transformer.transform(failedResult.getRawFailure());
                        //TODO should we actually be doing this? It seems we should only do it for the blocking case.
                        BlockingResultHandler.attachCallerThreadStackTrace(transformedFailure);
                        if (failedResult.getProjectIdentifier() != null) {
                            return new DefaultFailedModelResult<T>(failedResult.getProjectIdentifier(), transformedFailure);
                        } else {
                            return new DefaultFailedModelResult<T>(failedResult.getBuildIdentifier(), transformedFailure);
                        }
                    } else {
                        return next;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
