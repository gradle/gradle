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

package org.gradle.api.internal.externalresource.transfer;

import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

public class AbstractProgressLoggingHandler {
    protected final ProgressLoggerFactory progressLoggerFactory;
    private long loggedKBytes;

    public AbstractProgressLoggingHandler(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    protected ProgressLogger startProgress(String description, Class loggingClass) {
        this.loggedKBytes = 0;
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(loggingClass != null ? loggingClass : getClass());
        progressLogger.setDescription(description);
        progressLogger.setLoggingHeader(description);
        progressLogger.started();
        return progressLogger;
    }

    protected String getLengthText(Long bytes) {
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

    protected void logProgress(ProgressLogger progressLogger, Long processed, Long contentLength, String operationString) {
        if (progressLogger != null) {
            long processedKB = processed / 1024;
            if (processedKB > loggedKBytes) {
                loggedKBytes = processedKB;
                final String progressMessage = String.format("%s/%s %s", getLengthText(processed), getLengthText(contentLength != 0 ? contentLength : null), operationString);
                progressLogger.progress(progressMessage);
            }
        }

    }
}
