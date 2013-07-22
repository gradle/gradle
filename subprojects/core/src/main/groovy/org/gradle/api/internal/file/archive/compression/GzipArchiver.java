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

package org.gradle.api.internal.file.archive.compression;

import org.gradle.api.internal.resources.URIBuilder;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipArchiver implements ReadableResource {

    private ReadableResource resource;
    private URI uri;

    public GzipArchiver(ReadableResource resource) {
        assert resource != null;
        this.resource = resource;
        this.uri = new URIBuilder(resource.getURI()).schemePrefix("gzip:").build();
    }

    public static ArchiveOutputStreamFactory getCompressor() {
        // this is not very beautiful but at some point we will
        // get rid of ArchiveOutputStreamFactory in favor of the writable Resource
        return new ArchiveOutputStreamFactory() {
            public OutputStream createArchiveOutputStream(File destination) {
                try {
                    OutputStream outStr = new FileOutputStream(destination);
                    return new GZIPOutputStream(outStr);
                } catch (Exception e) {
                    String message = String.format("Unable to create gzip output stream for file %s.", destination);
                    throw new RuntimeException(message, e);
                }
            }
        };
    }

    public InputStream read() {
        InputStream is = resource.read();
        try {
            return new GZIPInputStream(is);
        } catch (Exception e) {
            String message = String.format("Unable to create gzip input stream for resource %s.", resource.getDisplayName());
            throw new ResourceException(message, e);
        }
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }

    public URI getURI() {
        return uri;
    }

    public String getBaseName() {
        return resource.getBaseName();
    }
}
