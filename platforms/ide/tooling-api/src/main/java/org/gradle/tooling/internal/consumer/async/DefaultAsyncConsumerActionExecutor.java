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
package org.gradle.tooling.internal.consumer.async;

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerActionExecutor;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;

/**
 * Adapts a {@link ConsumerActionExecutor} to an {@link AsyncConsumerActionExecutor}.
 */
public class DefaultAsyncConsumerActionExecutor implements AsyncConsumerActionExecutor {
    private final ConsumerActionExecutor actionExecutor;
    private final ManagedExecutor executor;
    private final ServiceLifecycle lifecycle;

    public DefaultAsyncConsumerActionExecutor(ConsumerActionExecutor actionExecutor, ExecutorFactory executorFactory) {
        this.actionExecutor = actionExecutor;
        executor = executorFactory.create("Connection worker");
        lifecycle = new ServiceLifecycle(actionExecutor.getDisplayName());
    }

    @Override
    public String getDisplayName() {
        return actionExecutor.getDisplayName();
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(lifecycle, executor, actionExecutor).stop();
    }

    @Override
    public void disconnect() {
        lifecycle.requestStop();
        executor.requestStop();
        actionExecutor.disconnect();
    }

    @Override
    public <T> void run(final ConsumerAction<? extends T> action, final ResultHandlerVersion1<? super T> handler) {
        lifecycle.use(new Runnable() {
            @Override
            public void run() {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        T result;
                        try {
                            result = actionExecutor.run(action);
                        } catch (Throwable t) {
                            handler.onFailure(t);
                            return;
                        }
                        handler.onComplete(result);
                    }
                });
            }
        });
    }
}
