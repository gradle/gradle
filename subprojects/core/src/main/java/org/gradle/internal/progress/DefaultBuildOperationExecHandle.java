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
import org.gradle.internal.operations.BuildOperationExecHandle;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Clock;

public class DefaultBuildOperationExecHandle implements org.gradle.internal.operations.BuildOperationExecHandle {
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final BuildOperationListener listener;
    private final Clock clock;
    private final BuildOperationDescriptor descriptor;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationState parent;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationState currentOperation;
    private final DefaultBuildOperationExecutor.DefaultBuildOperationContext context;

    public DefaultBuildOperationExecHandle(BuildOperationIdFactory buildOperationIdFactory, BuildOperationListener listener, Clock clock, BuildOperationDescriptor descriptor, DefaultBuildOperationExecutor.DefaultBuildOperationState parent,
                                           DefaultBuildOperationExecutor.DefaultBuildOperationState currentOperation) {
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.listener = listener;
        this.clock = clock;
        this.descriptor = descriptor;
        this.parent = parent;
        this.currentOperation = currentOperation;
        this.context = new DefaultBuildOperationExecutor.DefaultBuildOperationContext();
    }

    @Override
    public BuildOperationExecHandle startChild(BuildOperationDescriptor.Builder descriptionBuilder) {
        BuildOperationDescriptor descriptor = createChildDescriptor(descriptionBuilder);
        DefaultBuildOperationExecutor.DefaultBuildOperationState childOperation = new DefaultBuildOperationExecutor.DefaultBuildOperationState(descriptor, clock.getCurrentTime());
        assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, currentOperation);
        childOperation.setRunning(true);
        listener.started(descriptor, new OperationStartEvent(childOperation.getStartTime()));
        return new DefaultBuildOperationExecHandle(buildOperationIdFactory, listener, clock, descriptor, parent, childOperation);
    }

    @Override
    public void emitChildBuildOperation(RunnableBuildOperation buildOperation) {
        BuildOperationDescriptor descriptor = createChildDescriptor(buildOperation.description());
        DefaultBuildOperationExecutor.DefaultBuildOperationState childOperation = new DefaultBuildOperationExecutor.DefaultBuildOperationState(descriptor, clock.getCurrentTime());
        assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, currentOperation);
        childOperation.setRunning(true);
        listener.started(descriptor, new OperationStartEvent(childOperation.getStartTime()));
        DefaultBuildOperationExecutor.DefaultBuildOperationContext context = new DefaultBuildOperationExecutor.DefaultBuildOperationContext();
        try {
            buildOperation.run(context);
        } catch (Throwable t) {
            context.thrown(t);
        } finally {
            listener.finished(descriptor, new OperationFinishEvent(childOperation.getStartTime(), clock.getCurrentTime(), context.failure, context.result));
            childOperation.setRunning(false);
        }
    }

    @Override
    public void finish(Object result) {
        context.setResult(result);
        doFinish(result, null);
    }

    public void failed(Throwable t) {
        context.failed(t);
        doFinish(null, t);
    }

    /**
     * TODO check if all childs have finished too
     */
    private void doFinish(Object result, Throwable failure) {
        listener.finished(descriptor, new OperationFinishEvent(currentOperation.getStartTime(), clock.getCurrentTime(), failure, result));
        currentOperation.setRunning(false);
    }

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

}
