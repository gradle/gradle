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

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingMavenTransferListener implements TransferListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMavenTransferListener.class);
    private static final int KILO = 1024;

    protected void log(String message) {
        LOGGER.info(message);
    }

    public void debug(String s) {
        LOGGER.debug(s);
    }

    public void transferError(TransferEvent event) {
        LOGGER.error(event.getException().getMessage());
    }

    public void transferInitiated(TransferEvent event) {
        String message = event.getRequestType() == TransferEvent.REQUEST_PUT ? "Uploading" : "Downloading";
        String dest = event.getRequestType() == TransferEvent.REQUEST_PUT ? " to " : " from ";

        LOGGER.info(message + ": " + event.getResource().getName() + dest + "repository "
                + event.getWagon().getRepository().getId() + " at " + event.getWagon().getRepository().getUrl());
    }

    public void transferStarted(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if (contentLength > 0) {
            LOGGER.info("Transferring " + ((contentLength + KILO / 2) / KILO) + "K from "
                    + event.getWagon().getRepository().getId());
        }
    }

    public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    }

    public void transferCompleted(TransferEvent event) {
        long contentLength = event.getResource().getContentLength();
        if ((contentLength > 0) && (event.getRequestType() == TransferEvent.REQUEST_PUT)) {
            LOGGER.info("Uploaded " + ((contentLength + KILO / 2) / KILO) + "K");
        }
    }
}
