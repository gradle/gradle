/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.commons.lang.StringUtils;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.plugins.repository.TransferListener;
import org.gradle.api.internal.externalresource.transfer.AbstractProgressLoggingHandler;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

public class ProgressLoggingTransferListener extends AbstractProgressLoggingHandler implements TransferListener {
    private final Class loggingClass;
    private ProgressLogger logger;
    private long totalProcessed;

    public ProgressLoggingTransferListener(ProgressLoggerFactory progressLoggerFactory, Class loggingClass) {
        super(progressLoggerFactory);
        this.loggingClass = loggingClass;
    }

    public void transferProgress(TransferEvent evt) {
        final Resource resource = evt.getResource();
        if (resource.isLocal()) {
            return;
        }
        final int eventType = evt.getEventType();
        if (eventType == TransferEvent.TRANSFER_STARTED) {
            totalProcessed = 0;
            String description = String.format("%s %s", StringUtils.capitalize(getRequestType(evt)), resource.getName());
            logger = startProgress(description, loggingClass);
        }
        if (eventType == TransferEvent.TRANSFER_PROGRESS) {
            totalProcessed += evt.getLength();
            logProgress(logger, totalProcessed, evt.getTotalLength(), getRequestType(evt) + "ed");
        }
        if (eventType == TransferEvent.TRANSFER_COMPLETED) {
            logger.completed();
        }
    }

    private String getRequestType(TransferEvent evt) {
        if (evt.getRequestType() == TransferEvent.REQUEST_PUT) {
            return "upload";
        } else {
            return "download";
        }
    }
}
