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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;

/**
 * The idea is to initialize the logging infrastructure before we actually build the model or run a build.
 */
public class LoggingInitializerConnection implements ConsumerConnection {

    private final ConsumerConnection connection;
    private final SynchronizedLogging synchronizedLogging;

    public LoggingInitializerConnection(ConsumerConnection connection, SynchronizedLogging synchronizedLogging) {
        this.connection = connection;
        this.synchronizedLogging = synchronizedLogging;
    }

    public void stop() {
        connection.stop();
    }

    public String getDisplayName() {
        return connection.getDisplayName();
    }

    public VersionDetails getVersionDetails() {
        return connection.getVersionDetails();
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        synchronizedLogging.init();
        return connection.run(type, operationParameters);
    }
}
