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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferEvent.RequestType;

class LoggingMavenTransferListener extends AbstractTransferListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMavenTransferListener.class);
    private static final int KILO = 1024;

    public void transferFailed(TransferEvent event) {
        LOGGER.error(event.getException().getMessage());
    }

    public void transferInitiated(TransferEvent event) {
        String message = event.getRequestType() == RequestType.PUT ? "Uploading: {} to repository {} at {}" : "Downloading: {} from repository {} at {}";
        LOGGER.info(message, event.getResource().getResourceName(), "remote", event.getResource().getRepositoryUrl());
    }

    public void transferStarted(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if (contentLength > 0) {
            LOGGER.info("Transferring {}K from remote", (contentLength + KILO / 2) / KILO);
        }
    }

    public void transferSucceeded(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if (contentLength > 0 && event.getRequestType() == RequestType.PUT) {
            LOGGER.info("Uploaded {}K", (contentLength + KILO / 2) / KILO);
        }
    }
}
