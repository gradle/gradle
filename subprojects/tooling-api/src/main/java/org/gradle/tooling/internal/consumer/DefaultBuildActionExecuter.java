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

import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

class DefaultBuildActionExecuter<T> extends AbstractLongRunningOperation<DefaultBuildActionExecuter<T>> implements BuildActionExecuter<T> {
    private final BuildAction<T> buildAction;
    private final AsyncConsumerActionExecutor connection;

    public DefaultBuildActionExecuter(BuildAction<T> buildAction, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        this.buildAction = buildAction;
        this.connection = connection;
    }

    @Override
    protected DefaultBuildActionExecuter<T> getThis() {
        return this;
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
                               return connection.run(buildAction, operationParameters);
                           }
                       }, new ResultHandlerAdapter<T>(handler) {
                           @Override
                           protected String connectionFailureMessage(Throwable failure) {
                               return String.format("Could not run build action using %s.", connection.getDisplayName());
                           }
                       }
        );
    }
}
