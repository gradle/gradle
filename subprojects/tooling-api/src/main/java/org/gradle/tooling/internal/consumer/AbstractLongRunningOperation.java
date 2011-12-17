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

import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class AbstractLongRunningOperation implements LongRunningOperation {
    private OutputStream stdout;
    private OutputStream stderr;
    private InputStream stdin;

    private final ProgressListenerAdapter progressListener = new ProgressListenerAdapter();
    private final ConnectionParameters parameters;

    public AbstractLongRunningOperation(ConnectionParameters parameters) {
        this.parameters = parameters;
    }

    public AbstractLongRunningOperation setStandardOutput(OutputStream outputStream) {
        stdout = outputStream;
        return this;
    }

    public AbstractLongRunningOperation setStandardError(OutputStream outputStream) {
        stderr = outputStream;
        return this;
    }

    public AbstractLongRunningOperation setStandardInput(InputStream inputStream) {
        stdin = inputStream;
        return this;
    }

    public AbstractLongRunningOperation addProgressListener(ProgressListener listener) {
        progressListener.add(listener);
        return this;
    }

    protected BuildOperationParametersVersion1 operationParameters() {
        ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
        OperationParameters params = new OperationParameters();
        BuildOperationParametersVersion1 adapted = adapter.adapt(BuildOperationParametersVersion1.class, params);
        return adapted;
    }

    private class OperationParameters implements BuildOperationParametersVersion1 {
        long startTime = System.currentTimeMillis();

        public long getStartTime() {
            return startTime;
        }

        public boolean getVerboseLogging() {
            return parameters.getVerboseLogging();
        }

        public File getGradleUserHomeDir() {
            return parameters.getGradleUserHomeDir();
        }

        public File getProjectDir() {
            return parameters.getProjectDir();
        }

        public Boolean isSearchUpwards() {
            return parameters.isSearchUpwards();
        }

        public Boolean isEmbedded() {
            return parameters.isEmbedded();
        }

        public TimeUnit getDaemonMaxIdleTimeUnits() {
            return parameters.getDaemonMaxIdleTimeUnits();
        }

        public Integer getDaemonMaxIdleTimeValue() {
            return parameters.getDaemonMaxIdleTimeValue();
        }

        public OutputStream getStandardOutput() {
            return stdout;
        }

        public OutputStream getStandardError() {
            return stderr;
        }

        public ProgressListenerVersion1 getProgressListener() {
            return progressListener;
        }

        public InputStream getStandardInput() {
            return stdin;
        }
    }
}
