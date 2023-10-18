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

package org.gradle.problems.internal;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

public class OperationListener implements BuildOperationListener {

    private final ThreadLocal<ConcurrentHashMap<OperationIdentifier, Object>> runningOps = ThreadLocal.withInitial(ConcurrentHashMap::new);

    static final Object NO_DETAILS = new Object();
    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        Object details = buildOperation.getDetails();
        if (details == null) {
            details = NO_DETAILS;
        }
        runningOps.get().put(mandatoryIdOf(buildOperation), details);
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        runningOps.get().remove(mandatoryIdOf(buildOperation));
    }

    private OperationIdentifier mandatoryIdOf(BuildOperationDescriptor buildOperation) {
        OperationIdentifier id = buildOperation.getId();
        if (id == null) {
            throw new IllegalStateException(format("Build operation %s has no valid id", buildOperation));
        }
        return id;
    }

    /**
     * Returns the operation with the given id.
     * Returns null if no such operation is running or if the operation is not of the given type.
     *
     * @param id the operation identifier
     * @param targetClass the expected type of the operation
     * @return the operation if it is running and of the given type, null otherwise
     * @param <T> the expected type of the operation
     */
    @Nullable
    public <T> T getOp(OperationIdentifier id, Class<T> targetClass) {
        Object op = runningOps.get().get(id);
        return targetClass.isInstance(op) ? targetClass.cast(op) : null;
    }
}
