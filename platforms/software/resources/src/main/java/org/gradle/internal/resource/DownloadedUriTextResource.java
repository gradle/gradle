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

import org.gradle.internal.file.RelativeFilePathResolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * A {@link TextResource} implementation backed by a {@link UriTextResource}. This helps hide the internal details about file caching.
 */
public class DownloadedUriTextResource extends UriTextResource {

    private final String contentType;
    private final File downloadedResource;

    public DownloadedUriTextResource(String description, URI sourceUri, String contentType, File downloadedResource, RelativeFilePathResolver resolver) {
        super(description, sourceUri, resolver);
        this.contentType = contentType;
        this.downloadedResource = downloadedResource;
    }

    @Override
    protected Reader openReader() throws IOException {
        Charset charset = extractCharacterEncoding(contentType, DEFAULT_ENCODING);
        InputStream inputStream = new FileInputStream(downloadedResource);
        return new InputStreamReader(inputStream, charset);
    }

    @Override
    public Charset getCharset() {
        return extractCharacterEncoding(contentType, DEFAULT_ENCODING);
    }
}
