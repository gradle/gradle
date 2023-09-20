/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

public class OperationListener implements BuildOperationListener {

    final Map<OperationIdentifier, Object> runningOps = new ConcurrentHashMap<>();

    static final Object NO_DETAILS = new Object();
    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        Object details = buildOperation.getDetails();
        if (details == null) {
            details = NO_DETAILS;
        }
        runningOps.put(mandatoryIdOf(buildOperation), details);
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        runningOps.remove(mandatoryIdOf(buildOperation));
    }

    private OperationIdentifier mandatoryIdOf(BuildOperationDescriptor buildOperation) {
        OperationIdentifier id = buildOperation.getId();
        if (id == null) {
            throw new IllegalStateException(format("Build operation %s has no valid id", buildOperation));
        }
        return id;
    }
}
