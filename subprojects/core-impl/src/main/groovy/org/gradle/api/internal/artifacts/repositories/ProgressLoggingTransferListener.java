/*
 * Copyright 2011 the original author or authors.
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
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.plugins.repository.TransferListener;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

public class ProgressLoggingTransferListener implements TransferListener {
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Class loggingClass;
    private ProgressLogger logger;
    private long total;

    public ProgressLoggingTransferListener(ProgressLoggerFactory progressLoggerFactory, Class loggingClass) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.loggingClass = loggingClass;
    }

    public void transferProgress(TransferEvent evt) {
        if (evt.getResource().isLocal()) {
            return;
        }
        if (evt.getEventType() == TransferEvent.TRANSFER_STARTED) {
            total = 0;
            logger = progressLoggerFactory.newOperation(loggingClass);
            String description = String.format("%s %s", StringUtils.capitalize(getRequestType(evt)), evt.getResource().getName());
            logger.setDescription(description);
            logger.setLoggingHeader(description);
            logger.started();
        }
        if (evt.getEventType() == TransferEvent.TRANSFER_PROGRESS) {
            total += evt.getLength();
            logger.progress(String.format("%s/%s %sed", getLengthText(total), getLengthText(evt), getRequestType(evt)));
        }
        if (evt.getEventType() == TransferEvent.TRANSFER_COMPLETED) {
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

    private static String getLengthText(TransferEvent evt) {
        return getLengthText(evt.isTotalLengthSet() ? evt.getTotalLength() : null);
    }

    private static String getLengthText(Long bytes) {
        if (bytes == null) {
            return "unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1048576) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.2f MB", bytes / 1048576.0);
        }
    }

}
