/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.event.types.DefaultFileDownloadDescriptor;
import org.gradle.internal.build.event.types.DefaultOperationDescriptor;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType;

import java.net.URI;
import java.net.URISyntaxException;

public class ClientForwardingFileDownloadOperationListener implements BuildOperationListener {
    private final ProgressEventConsumer eventConsumer;

    public ClientForwardingFileDownloadOperationListener(ProgressEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ExternalResourceReadBuildOperationType.Details) {
            ExternalResourceReadBuildOperationType.Details details = (ExternalResourceReadBuildOperationType.Details) buildOperation.getDetails();
            DefaultOperationDescriptor descriptor = descriptor(buildOperation, details);
            eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor));
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof ExternalResourceReadBuildOperationType.Details) {
            ExternalResourceReadBuildOperationType.Details details = (ExternalResourceReadBuildOperationType.Details) buildOperation.getDetails();
            DefaultOperationDescriptor descriptor = descriptor(buildOperation, details);
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, ClientForwardingBuildOperationListener.toOperationResult(finishEvent)));
        }
    }

    private DefaultFileDownloadDescriptor descriptor(BuildOperationDescriptor buildOperation, ExternalResourceReadBuildOperationType.Details details) {
        try {
            return new DefaultFileDownloadDescriptor(buildOperation.getId(), buildOperation.getName(), buildOperation.getDisplayName(), eventConsumer.findStartedParentId(buildOperation), new URI(details.getLocation()));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }
}
