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

package org.gradle.internal.resource.transfer;

import org.gradle.internal.operations.BuildOperationContext;

import static org.gradle.internal.util.NumberUtil.KIB_BASE;
import static org.gradle.internal.util.NumberUtil.formatBytes;

public class ResourceOperation {
    public enum Type {
        download,
        upload;
    }

    private final BuildOperationContext context;
    private final Type operationType;
    private long contentLengthBytes;
    private String contentLengthString;

    private long loggedKBytes;
    private long totalProcessedBytes;

    public ResourceOperation(BuildOperationContext context, Type type) {
        this.context = context;
        this.operationType = type;
    }

    public void setContentLength(long contentLength) {
        this.contentLengthBytes = contentLength;
        if (contentLength <= 0) {
            this.contentLengthString = String.format(" %sed", operationType);
        } else {
            this.contentLengthString = String.format("/%s %sed", formatBytes(contentLength), operationType);
        }
    }

    public long getTotalProcessedBytes() {
        return totalProcessedBytes;
    }

    public void logProcessedBytes(long processedBytes) {
        totalProcessedBytes += processedBytes;
        long processedKiB = totalProcessedBytes / KIB_BASE;
        if (processedKiB > loggedKBytes) {
            loggedKBytes = processedKiB;
            String progressMessage = formatBytes(totalProcessedBytes) + contentLengthString;
            context.progress(totalProcessedBytes, contentLengthBytes, "bytes", progressMessage);
        }
    }
}
