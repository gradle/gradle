/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.tasks.testing.operations.TestListenerBuildOperationAdapter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.test.Destination;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTestOutputDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTestOutputEvent;
import org.gradle.tooling.internal.provider.events.DefaultTestOutputResult;

/**
 * Test listener that forwards the test output events.
 */
class ClientForwardingTestOutputOperationListener implements BuildOperationListener {

    private final BuildOperationListener delegate;
    private final ProgressEventConsumer eventConsumer;
    private final BuildOperationIdFactory idFactory;

    ClientForwardingTestOutputOperationListener(BuildOperationListener delegate, ProgressEventConsumer eventConsumer, BuildOperationIdFactory idFactory) {
        this.delegate = delegate;
        this.eventConsumer = eventConsumer;
        this.idFactory = idFactory;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        delegate.started(buildOperation, startEvent);
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        if (progressEvent.getDetails() instanceof TestListenerBuildOperationAdapter.OutputProgress) {
            TestListenerBuildOperationAdapter.OutputProgress progress = (TestListenerBuildOperationAdapter.OutputProgress) progressEvent.getDetails();
            InternalTestOutputDescriptor descriptor = new DefaultTestOutputDescriptor(new OperationIdentifier(idFactory.nextId()), progress.getTestDescriptorId());
            DefaultTestOutputResult result = new DefaultTestOutputResult(progressEvent.getTime(), progressEvent.getTime(), getDestination(progress.getOutput().getDestination()), progress.getOutput().getMessage());
            eventConsumer.progress(new DefaultTestOutputEvent(progressEvent.getTime(), descriptor, result));
        } else {
            delegate.progress(buildOperationId, progressEvent);
        }
    }

    private int getDestination(TestOutputEvent.Destination destination) {
        switch (destination) {
            case StdOut: return Destination.StdOut.getCode();
            case StdErr: return Destination.StdErr.getCode();
            default: throw new IllegalStateException("Unknown output destination type: " + destination);
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        delegate.finished(buildOperation,finishEvent);
    }
}
