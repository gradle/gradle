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

import org.gradle.listener.ListenerBroadcast;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.internal.protocol.LongRunningOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.OutputStream;

public class AbstractLongRunningOperation {
    private OutputStream stdout;
    private OutputStream stderr;
    private final ListenerBroadcast<ProgressListener> progressListener = new ListenerBroadcast<ProgressListener>(ProgressListener.class);

    public AbstractLongRunningOperation setStandardOutput(OutputStream outputStream) {
        stdout = outputStream;
        return this;
    }

    public AbstractLongRunningOperation setStandardError(OutputStream outputStream) {
        stderr = outputStream;
        return this;
    }

    public AbstractLongRunningOperation addProgressListener(ProgressListener listener) {
        progressListener.add(listener);
        return this;
    }

    protected LongRunningOperationParametersVersion1 operationParameters() {
        return new OperationParameters();
    }

    private class OperationParameters implements LongRunningOperationParametersVersion1 {
        public OutputStream getStandardOutput() {
            return stdout;
        }

        public OutputStream getStandardError() {
            return stderr;
        }

        public ProgressListenerVersion1 getProgressListener() {
            return new ProgressListenerVersion1() {
                public void statusChanged(final String description) {
                    progressListener.getSource().statusChanged(new ProgressEvent() {
                        public String getDescription() {
                            return description;
                        }
                    });
                }
            };
        }
    }

}
