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

package org.gradle.internal.progress;

import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.*;
import org.gradle.internal.operations.BuildOperationExecHandle;
import org.gradle.internal.time.Clock;

public class DefaultBuildOperationExecHandle implements org.gradle.internal.operations.BuildOperationExecHandle {
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildOperationListener listener;
    private final Clock clock;
    private final BuildOperationDescriptor descriptor;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationState parent;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationState currentOperation;
    private final ProgressLogger progressLogger;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationContext context;

    public DefaultBuildOperationExecHandle(BuildOperationIdFactory buildOperationIdFactory, ProgressLoggerFactory progressLoggerFactory, BuildOperationListener listener, Clock clock, BuildOperationDescriptor descriptor, DefaultBuildOperationExecutor.DefaultBuildOperationState parent,
                                           DefaultBuildOperationExecutor.DefaultBuildOperationState currentOperation,
                                           ProgressLogger progressLogger) {
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.listener = listener;
        this.clock = clock;
        this.descriptor = descriptor;
        this.parent = parent;
        this.currentOperation = currentOperation;
        this.progressLogger = progressLogger;
        this.context = new DefaultBuildOperationExecutor.DefaultBuildOperationContext();
    }

    @Override
    public void finish(Object result) {
        context.setResult(result);
        progressLogger.completed("Status", false);
        doFinish(result, null);
    }

    @Override
    public BuildOperationExecHandle startChild(BuildOperationDescriptor.Builder descriptionBuilder) {

            BuildOperationDescriptor descriptor = createChildDescriptor(descriptionBuilder);
            DefaultBuildOperationExecutor.DefaultBuildOperationState childOperation = new DefaultBuildOperationExecutor.DefaultBuildOperationState(descriptor, clock.getCurrentTime());

            assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, currentOperation);

            childOperation.setRunning(true);
            listener.started(descriptor, new OperationStartEvent(childOperation.getStartTime()));
            ProgressLogger progressLogger = createProgressLogger(childOperation);
//            LOGGER.debug("Build operation '{}' started", descriptor.getDisplayName());

            return new DefaultBuildOperationExecHandle(buildOperationIdFactory, progressLoggerFactory, listener, clock, descriptor, parent, childOperation, progressLogger);
//            LOGGER.debug("Completing Build operation '{}'", descriptor.getDisplayName());
//
//            progressLogger.completed(context.status, context.failure != null);
//            listener.finished(descriptor, new OperationFinishEvent(childOperation.getStartTime(), clock.getCurrentTime(), context.failure, context.result));
//
//            assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);
//
//            if (failure != null) {
//                throw UncheckedException.throwAsUncheckedException(failure, true);
//            }

//            LOGGER.debug("Build operation '{}' completed", descriptor.getDisplayName());
//        }

    }


    public void failed(Throwable t) {
        context.failed(t);
        progressLogger.completed("Status", false);
        doFinish(null, t);
    }

    /**
     * TODO check if all childs have finished too
     * */
    private void doFinish(Object result, Throwable failure) {
        listener.finished(descriptor, new OperationFinishEvent(currentOperation.getStartTime(), clock.getCurrentTime(), failure, result));

        assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);

        if (parent != null) {
            BuildOperationIdentifierRegistry.setCurrentOperationIdentifier(parent.getId());
        } else {
            BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier();
        }
        currentOperation.setRunning(false);
    }
//

    private BuildOperationDescriptor createChildDescriptor(BuildOperationDescriptor.Builder descriptorBuilder) {
        OperationIdentifier id = new OperationIdentifier(buildOperationIdFactory.nextId());
        return descriptorBuilder.build(id, currentOperation == null ? null : currentOperation.getDescription().getId());
    }

    private void assertParentRunning(String message, BuildOperationDescriptor child, DefaultBuildOperationExecutor.DefaultBuildOperationState parent) {
        if (parent != null && !parent.isRunning()) {
            String parentName = parent.getDescription().getDisplayName();
            throw new IllegalStateException(String.format(message, child.getDisplayName(), parentName));
        }
    }

    private ProgressLogger createProgressLogger(DefaultBuildOperationExecutor.DefaultBuildOperationState currentOperation) {
        BuildOperationDescriptor descriptor = currentOperation.getDescription();
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
        return progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
    }
}
