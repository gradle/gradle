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

import org.gradle.tooling.internal.protocol.*;

/**
 * The idea is to initialize the logging infrastructure before we actually build the model or run a build.
 * <p>
 * by Szczepan Faber, created at: 12/14/11
 */
public class LoggingInitializerConnection implements ConnectionVersion4 {

    private final ConnectionVersion4 connection;
    private final SynchronizedLogging synchronizedLogging;

    public LoggingInitializerConnection(ConnectionVersion4 connection, SynchronizedLogging synchronizedLogging) {
        this.connection = connection;
        this.synchronizedLogging = synchronizedLogging;
    }

    public void stop() {
        connection.stop();
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return connection.getMetaData();
    }

    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) throws UnsupportedOperationException, IllegalStateException {
        synchronizedLogging.init();
        return connection.getModel(type, operationParameters);
    }

    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) throws IllegalStateException {
        synchronizedLogging.init();
        connection.executeBuild(buildParameters, operationParameters);
    }
}
