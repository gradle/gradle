/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.Cast;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.IntermediateResultHandler;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.StreamedValueListener;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Arrays;

class DefaultBuildActionExecuter<T> extends AbstractLongRunningOperation<DefaultBuildActionExecuter<T>> implements BuildActionExecuter<T> {
    private final BuildAction<T> buildAction;
    private final AsyncConsumerActionExecutor connection;

    public DefaultBuildActionExecuter(BuildAction<T> buildAction, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setEntryPoint("BuildActionExecuter API");
        this.buildAction = buildAction;
        this.connection = connection;
    }

    @Override
    protected DefaultBuildActionExecuter<T> getThis() {
        return this;
    }

    @Override
    public void setStreamedValueListener(StreamedValueListener listener) {
        operationParamsBuilder.setStreamedValueListener(listener);
    }

    @Override
    public BuildActionExecuter<T> forTasks(String... tasks) {
        operationParamsBuilder.setTasks(tasks != null ? Arrays.asList(tasks) : null);
        return getThis();
    }

    @Override
    public BuildActionExecuter<T> forTasks(Iterable<String> tasks) {
        operationParamsBuilder.setTasks(tasks != null ? CollectionUtils.toList(tasks) : null);
        return getThis();
    }

    @Override
    public T run() throws GradleConnectionException {
        BlockingResultHandler<Object> handler = new BlockingResultHandler<Object>(Object.class);
        run(handler);
        return Cast.uncheckedNonnullCast(handler.getResult());
    }

    @Override
    public void run(ResultHandler<? super T> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<T>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            @Override
            public T run(ConsumerConnection connection) {
                T result = connection.run(buildAction, operationParameters);
                return result;
            }
        }, new ResultHandlerAdapter<T>(handler, createExceptionTransformer(new ConnectionExceptionTransformer.ConnectionFailureMessageProvider() {
            @Override
            public String getConnectionFailureMessage(Throwable throwable) {
                return String.format("Could not run build action using %s.", connection.getDisplayName());
            }
        })));
    }

    static class Builder implements BuildActionExecuter.Builder {
        @Nullable
        private PhasedBuildAction.BuildActionWrapper<?> projectsLoadedAction = null;
        @Nullable
        private PhasedBuildAction.BuildActionWrapper<?> buildFinishedAction = null;

        private final AsyncConsumerActionExecutor connection;
        private final ConnectionParameters parameters;

        Builder(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
            this.connection = connection;
            this.parameters = parameters;
        }

        @Override
        public <T> Builder projectsLoaded(BuildAction<T> action, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException {
            if (projectsLoadedAction != null) {
                throw getException("ProjectsLoadedAction");
            }
            projectsLoadedAction = new DefaultPhasedBuildAction.DefaultBuildActionWrapper<T>(action, handler);
            return Builder.this;
        }

        @Override
        public <T> Builder buildFinished(BuildAction<T> action, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException {
            if (buildFinishedAction != null) {
                throw getException("BuildFinishedAction");
            }
            buildFinishedAction = new DefaultPhasedBuildAction.DefaultBuildActionWrapper<T>(action, handler);
            return Builder.this;
        }

        @Override
        public BuildActionExecuter<Void> build() {
            return new DefaultPhasedBuildActionExecuter(new DefaultPhasedBuildAction(projectsLoadedAction, buildFinishedAction), connection, parameters);
        }

        private static IllegalArgumentException getException(String phase) {
            return new IllegalArgumentException(String.format("%s has already been added. Only one action per phase is allowed.", phase));
        }
    }
}
