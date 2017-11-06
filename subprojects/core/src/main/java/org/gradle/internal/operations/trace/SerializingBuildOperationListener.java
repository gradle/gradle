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
import java.util.Map;

/**
 * Note: this is relying on Gradle's listener infrastructure serializing dispatch
 * and prevent concurrent invocations of started/finished.
 */
class SerializingBuildOperationListener implements BuildOperationListener {

    private static final byte[] NEWLINE = "\n".getBytes();
    private static final byte[] INDENT = "    ".getBytes();

    private OutputStream out;

    SerializingBuildOperationListener(OutputStream out) {
        this.out = out;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        write(new SerializedOperationStart(buildOperation, startEvent).toMap(), false);
    }

    @Override
    public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
        write(new SerializedOperationProgress(buildOperation, progressEvent).toMap(), false);
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        write(new SerializedOperationFinish(buildOperation, finishEvent).toMap(), true);
    }

    private void write(Map<String, ?> entry, boolean indent) {
        String json = JsonOutput.toJson(entry);
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
