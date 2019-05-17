/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.publication.maven.internal.action;

import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.slf4j.Logger;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.MetadataNotFoundException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferEvent.RequestType;

class LoggingMavenTransferListener extends AbstractTransferListener {

    private static final Logger LOGGER = Logging.getLogger(LoggingMavenTransferListener.class);
    private static final int KILO = 1024;

    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final BuildOperationRef buildOperationRef;
    private final ThreadLocal<BuildOperationRef> previousBuildOperationRef = new ThreadLocal<BuildOperationRef>();

    /*
        Note: Aether implicitly uses a thread pool and tasks to perform transfers,
        so we manually propagate the current build operation ref so logging is correctly associated.
     */

    public LoggingMavenTransferListener(CurrentBuildOperationRef currentBuildOperationRef, BuildOperationRef buildOperationRef) {
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.buildOperationRef = buildOperationRef;
    }

    @Override
    public void transferInitiated(TransferEvent event) {
        previousBuildOperationRef.set(currentBuildOperationRef.get());
        currentBuildOperationRef.set(buildOperationRef);
        String message = event.getRequestType() == RequestType.PUT ? "Uploading: {} to repository {} at {}" : "Downloading: {} from repository {} at {}";
        LOGGER.info(message, event.getResource().getResourceName(), "remote", event.getResource().getRepositoryUrl());
    }

    @Override
    public void transferStarted(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if (contentLength > 0) {
            LOGGER.info("Transferring {}K from remote", (contentLength + KILO / 2) / KILO);
        }
    }

    @Override
    public void transferFailed(TransferEvent event) {
        if (event.getException() instanceof MetadataNotFoundException) {
            LOGGER.info(event.getException().getMessage());
        } else {
            LOGGER.error(event.getException().getMessage());
        }
        resetCurrentBuildOperationRef();
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if (contentLength > 0 && event.getRequestType() == RequestType.PUT) {
            LOGGER.info("Uploaded {}K", (contentLength + KILO / 2) / KILO);
        }
        resetCurrentBuildOperationRef();
    }

    private void resetCurrentBuildOperationRef() {
        currentBuildOperationRef.set(previousBuildOperationRef.get());
    }
}
