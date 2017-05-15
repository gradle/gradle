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

package org.gradle.internal.resource;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.net.URI;

/**
 * The transfer of an external resource to the local system.
 *
 * @since 4.0
 */
public final class ExternalResourceDownloadBuildOperationType implements BuildOperationType<ExternalResourceDownloadBuildOperationType.Details, ExternalResourceDownloadBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        /**
         * Value is a valid URI.
         */
        String getLocation();

        /**
         * The advertised length of the resource, prior to transfer.
         * <p>
         * -1 if this is not known.
         */
        long getContentLength();

        String getContentType();

    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * The actual length of the received content.
         * <p>
         * Should be equal to {@link ExternalResourceDownloadBuildOperationType.Details#getContentLength()} if it was not -1.
         * See {@link ExternalResourceReadResult#getReadContentLength()} for the semantics of this value.
         */
        long getReadContentLength();

    }

    static class DetailsImpl implements Details {

        private final URI location;

        private final long contentLength;
        private final String contentType;

        DetailsImpl(URI location, long contentLength, String contentType) {
            this.location = location;
            this.contentLength = contentLength;
            this.contentType = contentType;
        }

        public String getLocation() {
            return location.toASCIIString();
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public String toString() {
            return "ExternalResourceDownloadBuildOperationType.Details{"
                + "location=" + location + ", "
                + "contentLength=" + contentLength + ", "
                + "contentType='" + contentType + '\''
                + '}';
        }

    }

    static class ResultImpl implements Result {

        private final long readContentLength;

        ResultImpl(long readContentLength) {
            this.readContentLength = readContentLength;
        }

        @Override
        public long getReadContentLength() {
            return readContentLength;
        }

        @Override
        public String toString() {
            return "ExternalResourceDownloadBuildOperationType.Result{"
                + "readContentLength=" + readContentLength
                + '}';
        }

    }

    private ExternalResourceDownloadBuildOperationType() {
    }

}
