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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.consumer.SynchronizedLogging;

/**
 * The idea is to initialize the logging infrastructure before we actually build the model or run a build.
 */
public class LoggingInitializerConsumerActionExecutor implements ConsumerActionExecutor {

    private final ConsumerActionExecutor actionExecutor;
    private final SynchronizedLogging synchronizedLogging;

    public LoggingInitializerConsumerActionExecutor(ConsumerActionExecutor actionExecutor, SynchronizedLogging synchronizedLogging) {
        this.actionExecutor = actionExecutor;
        this.synchronizedLogging = synchronizedLogging;
    }

    public void stop() {
        actionExecutor.stop();
    }

    public String getDisplayName() {
        return actionExecutor.getDisplayName();
    }

    public <T> T run(ConsumerAction<T> action) throws UnsupportedOperationException, IllegalStateException {
        synchronizedLogging.init();
        return actionExecutor.run(action);
    }
}
