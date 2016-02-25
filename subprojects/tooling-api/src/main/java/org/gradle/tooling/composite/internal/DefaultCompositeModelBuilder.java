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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.consumer.BlockingResultHandler;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.ResultHandlerAdapter;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class DefaultCompositeModelBuilder<T> extends AbstractLongRunningOperation<DefaultCompositeModelBuilder<T>> implements ModelBuilder<Set<T>> {
    private final Class<T> modelType;
    private final AsyncConsumerActionExecutor connection;

    protected DefaultCompositeModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor connection, CompositeConnectionParameters parameters) {
        super(parameters);
        this.modelType = modelType;
        this.connection = connection;
        operationParamsBuilder.setEntryPoint("CompositeModelBuilder API");
    }

    @Override
    protected DefaultCompositeModelBuilder<T> getThis() {
        return this;
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        BlockingResultHandler<Set> handler = new BlockingResultHandler<Set>(Set.class);
        get(handler);
        return handler.getResult();
    }

    @Override
    public void get(final ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<Set<T>>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            public Set<T> run(ConsumerConnection connection) {
                Set<T> model = connection.buildModels(modelType, operationParameters);
                return model;
            }
        }, new ResultHandlerAdapter<Set<T>>(handler) {
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                String message = String.format("Could not fetch models of type '%s' using %s.", modelType.getSimpleName(), connection.getDisplayName());
                if (!(failure instanceof UnsupportedMethodException) && failure instanceof UnsupportedOperationException) {
                    message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
                }
                return message;
            }
        });
    }

    // TODO: Make all configuration methods configure underlying model builders
    private DefaultCompositeModelBuilder<T> unsupportedMethod() {
        throw new UnsupportedMethodException("Not supported for composite connections.");
    }

    @Override
    public DefaultCompositeModelBuilder<T> forTasks(String... tasks) {
        return forTasks(Lists.newArrayList(tasks));
    }

    @Override
    public DefaultCompositeModelBuilder<T> forTasks(Iterable<String> tasks) {
        return unsupportedMethod();
    }


    @Override
    public DefaultCompositeModelBuilder<T> withArguments(String... arguments) {
        return withArguments(Lists.newArrayList(arguments));
    }

    @Override
    public DefaultCompositeModelBuilder<T> withArguments(Iterable<String> arguments) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setStandardError(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setColorOutput(boolean colorOutput) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setStandardInput(InputStream inputStream) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setJavaHome(File javaHome) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setJvmArguments(String... jvmArguments) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> setJvmArguments(Iterable<String> jvmArguments) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        return unsupportedMethod();
    }

    @Override
    public DefaultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        return addProgressListener(listener, Sets.newHashSet(operationTypes));
    }

    @Override
    public DefaultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        return unsupportedMethod();
    }
}
