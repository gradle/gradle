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
 * A {@link ConnectionVersion4} implementation which provides some high-level progress information.
 */
public class ProgressLoggingConnection implements ConnectionVersion4 {
    private final ConnectionVersion4 connection;

    public ProgressLoggingConnection(ConnectionVersion4 connection) {
        this.connection = connection;
    }

    public void stop() {
        connection.stop();
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return connection.getMetaData();
    }

    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        ProgressListenerVersion1 listener = operationParameters.getProgressListener();
        listener.onOperationStart("Running build");
        try {
            connection.executeBuild(buildParameters, operationParameters);
        } finally {
            listener.onOperationEnd();
        }
    }

    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        ProgressListenerVersion1 listener = operationParameters.getProgressListener();
        listener.onOperationStart("Loading projects");
        try {
            return connection.getModel(type, operationParameters);
        } finally {
            listener.onOperationEnd();
        }
    }
}
