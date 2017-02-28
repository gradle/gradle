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

import org.gradle.api.Transformer;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

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
    public BuildActionExecuter<T> forTasks(String... tasks) {
        operationParamsBuilder.setTasks(rationalizeInput(tasks));
        return getThis();
    }

    @Override
    public BuildActionExecuter<T> forTasks(Iterable<String> tasks) {
        operationParamsBuilder.setTasks(rationalizeInput(tasks));
        return getThis();
    }

    public T run() throws GradleConnectionException {
        BlockingResultHandler<Object> handler = new BlockingResultHandler<Object>(Object.class);
        run(handler);
        return (T) handler.getResult();
    }

    public void run(ResultHandler<? super T> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<T>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            public T run(ConsumerConnection connection) {
                T result = connection.run(buildAction, operationParameters);
                return result;
            }
        }, new ResultHandlerAdapter<T>(handler, new ExceptionTransformer(new Transformer<String, Throwable>() {
            @Override
            public String transform(Throwable throwable) {
                return String.format("Could not run build action using %s.", connection.getDisplayName());
            }
        })));
    }
}
