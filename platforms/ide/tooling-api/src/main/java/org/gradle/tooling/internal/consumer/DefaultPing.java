/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.tooling.Ping;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.util.Collections;

public class DefaultPing extends AbstractLongRunningOperation<DefaultPing> implements Ping {
    private final AsyncConsumerActionExecutor connection;

    public DefaultPing(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setEntryPoint("Ping");
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
        this.connection = connection;
    }

    @Override
    protected DefaultPing getThis() {
        return this;
    }

    @Override
    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    @Override
    public void run(ResultHandler<Void> handler) {
        final ConsumerOperationParameters consumerOperationParameters = getConsumerOperationParameters();
        //BlockingResultHandler<Void> handler = new BlockingResultHandler<>(Void.class);
        connection.run(new ConsumerAction<Void>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return consumerOperationParameters;
            }

            @Override
            public Void run(ConsumerConnection connection) {
                connection.ping(consumerOperationParameters);
                return null;
            }
        }, new ResultHandlerAdapter<>(handler, new ConnectionExceptionTransformer(new ConnectionExceptionTransformer.ConnectionFailureMessageProvider() {
            @Override
            public String getConnectionFailureMessage(Throwable throwable) {
                return String.format("Could not ping using %s.", connection.getDisplayName());
            }
        })));
    }
}
