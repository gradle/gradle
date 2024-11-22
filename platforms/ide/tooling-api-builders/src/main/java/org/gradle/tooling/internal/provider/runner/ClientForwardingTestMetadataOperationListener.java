/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.operations.TestListenerBuildOperationAdapter;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.internal.build.event.types.DefaultTestMetadataDescriptor;
import org.gradle.internal.build.event.types.DefaultTestMetadataEvent;
import org.gradle.internal.build.event.types.DefaultTestMetadataResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor;

/**
 * Test listener that forwards the test metadata events.
 */
@NonNullApi
/* package */ class ClientForwardingTestMetadataOperationListener implements BuildOperationListener {
    private final MetadataEventConsumer eventConsumer;
    private final BuildOperationIdFactory idFactory;

    /* package */ ClientForwardingTestMetadataOperationListener(MetadataEventConsumer eventConsumer, BuildOperationIdFactory idFactory) {
        this.eventConsumer = eventConsumer;
        this.idFactory = idFactory;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details instanceof TestListenerBuildOperationAdapter.Metadata) {
            TestListenerBuildOperationAdapter.Metadata metadata = (TestListenerBuildOperationAdapter.Metadata) details;
            InternalTestMetadataDescriptor descriptor = new DefaultTestMetadataDescriptor(new OperationIdentifier(idFactory.nextId()), buildOperationId);
            TestMetadataEvent metadataMetadataEvent = metadata.getMetadata();
            DefaultTestMetadataResult result = new DefaultTestMetadataResult(progressEvent.getTime(), progressEvent.getTime(), metadataMetadataEvent.getKey(), metadataMetadataEvent.getValue());
            eventConsumer.metadata(new DefaultTestMetadataEvent(progressEvent.getTime(), descriptor, result));
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
    }
}
