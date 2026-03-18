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

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.api.tasks.testing.TestFileAttachmentDataEvent;
import org.gradle.api.tasks.testing.TestKeyValueDataEvent;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.internal.Cast;
import org.gradle.internal.build.event.types.DefaultTestFileAttachmentMetadataEvent;
import org.gradle.internal.build.event.types.DefaultTestKeyValueDataMetadataEvent;
import org.gradle.internal.build.event.types.DefaultTestMetadataDescriptor;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor;
import org.jspecify.annotations.NullMarked;

/**
 * Test listener that forwards the test metadata events.
 *
 * This listener adapts build operation test events sent from the build into internal test events shared with the consumer.
 */
@NullMarked
/* package */ class ClientForwardingTestMetadataOperationListener implements BuildOperationListener {
    private final ProgressEventConsumer eventConsumer;
    private final BuildOperationIdFactory idFactory;

    /* package */ ClientForwardingTestMetadataOperationListener(ProgressEventConsumer eventConsumer, BuildOperationIdFactory idFactory) {
        this.eventConsumer = eventConsumer;
        this.idFactory = idFactory;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details instanceof ExecuteTestBuildOperationType.Metadata) {
            ExecuteTestBuildOperationType.Metadata dataEvent = (ExecuteTestBuildOperationType.Metadata) details;
            InternalTestMetadataDescriptor descriptor = new DefaultTestMetadataDescriptor(new OperationIdentifier(idFactory.nextId()), buildOperationId);
            TestMetadataEvent data = dataEvent.getMetadata();
            if (data instanceof TestKeyValueDataEvent) {
                TestKeyValueDataEvent keyValueData = (TestKeyValueDataEvent) data;
                eventConsumer.progress(new DefaultTestKeyValueDataMetadataEvent(progressEvent.getTime(), descriptor, Cast.uncheckedNonnullCast(keyValueData.getValues())));
            } else if (data instanceof TestFileAttachmentDataEvent) {
                TestFileAttachmentDataEvent fileAttachment = (TestFileAttachmentDataEvent) data;
                eventConsumer.progress(new DefaultTestFileAttachmentMetadataEvent(progressEvent.getTime(), descriptor, fileAttachment.getPath().toFile(), fileAttachment.getMediaType()));
            } else {
                throw new IllegalStateException("Unexpected test metadata event type: " + data.getClass());
            }
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
    }
}
