/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.resource.ExternalResourceReadResult;

import java.net.URI;


/**
 * Details about some resource being downloaded.
 * <p>
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public final class DownloadBuildOperationDetails implements BuildOperationDetails<DownloadBuildOperationDetails.Result> {
    private final URI location;
    private final long contentLength;
    private final String contentType;

    public DownloadBuildOperationDetails(URI location, long contentLength, String contentType) {
        this.location = location;
        this.contentLength = contentLength;
        this.contentType = contentType;
    }

    public URI getLocation() {
        return location;
    }

    /**
     * The advertised length of the resource, prior to transfer.
     * <p>
     * -1 if this is not known.
     */
    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return "DownloadBuildOperationDetails{"
            + "location=" + location + ", "
            + "contentLength=" + contentLength + ", "
            + "contentType='" + contentType + '\''
            + '}';
    }

    /**
     * @since 4.0
     */
    public static class Result {

        private final long readContentLength;

        public Result(long readContentLength) {
            this.readContentLength = readContentLength;
        }

        /**
         * The actual length of the received content.
         * <p>
         * Should be equal to {@link DownloadBuildOperationDetails#getContentLength()} if it was not -1.
         * See {@link ExternalResourceReadResult#getReadContentLength()} for the semantics of this value.
         */
        public long getReadContentLength() {
            return readContentLength;
        }

        @Override
        public String toString() {
            return "DownloadBuildOperationDetails.Result{"
                + "readContentLength=" + readContentLength
                + '}';
        }
    }
}
