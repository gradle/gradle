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

import org.gradle.internal.progress.NoResultBuildOperationDetails;

import java.net.URI;


/**
 * Details about some resource being downloaded.
 *
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public final class DownloadBuildOperationDetails implements NoResultBuildOperationDetails {
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

    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

}
