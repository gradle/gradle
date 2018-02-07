/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.trace;

import groovy.json.JsonOutput;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationProgressEvent;
import org.gradle.internal.progress.OperationStartEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Note: this is relying on Gradle's listener infrastructure serializing dispatch
 * and prevent concurrent invocations of started/finished.
 */
class SerializingBuildOperationListener implements BuildOperationListener {

    private static final byte[] NEWLINE = "\n".getBytes();
    private static final byte[] INDENT = "    ".getBytes();

    private final OutputStream out;

    SerializingBuildOperationListener(OutputStream out) {
        this.out = out;
    }

    private boolean buffering = true;
    private final Lock bufferLock = new ReentrantLock();
    private final Queue<Entry> buffer = new ArrayDeque<Entry>();

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        new Entry(new SerializedOperationStart(buildOperation, startEvent), false).add();
    }

    @Override
    public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
        new Entry(new SerializedOperationProgress(buildOperation, progressEvent), false).add();
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        new Entry(new SerializedOperationFinish(buildOperation, finishEvent), false).add();
    }

    public void write() {
        if (buffering) {
            bufferLock.lock();
            try {
                if (buffering) {
                    for (Entry entry : buffer) {
                        entry.write();
                    }
                    buffer.clear();
                    buffering = false;
                }
            } finally {
                bufferLock.unlock();
            }
        }
    }

    private final class Entry {
        final SerializedOperation operation;
        final boolean indent;

        Entry(SerializedOperation operation, boolean indent) {
            this.operation = operation;
            this.indent = indent;
        }

        public void add() {
            if (buffering) {
                bufferLock.lock();
                try {
                    if (buffering) {
                        buffer.add(this);
                    } else {
                        write();
                    }
                } finally {
                    bufferLock.unlock();
                }
            } else {
                write();
            }
        }

        private void write() {
            String json = JsonOutput.toJson(operation.toMap());
            try {
                if (indent) {
                    out.write(INDENT);
                }
                out.write(json.getBytes("UTF-8"));
                out.write(NEWLINE);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

    }

}
