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

import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.resources.ReadableResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class GzipArchiver implements ReadableResource {

    private DescribedReadableResource resource;

    public GzipArchiver(DescribedReadableResource resource) {
        assert resource != null;
        this.resource = resource;
    }

    public static Compressor getCompressor() {
        // this is not very beautiful but at some point we will
        // get rid of Compressor in favor of the writable Resource
        return new Compressor() {
            public OutputStream compress(File destination) {
                try {
                    OutputStream outStr = new FileOutputStream(destination);
                    return new GZIPOutputStream(outStr);
                } catch (Exception e) {
                    String message = String.format("Unable to create gzip output stream for file: %s due to: %s ", destination, e.getMessage());
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
            String message = String.format("Unable to create gzip input stream for resource: %s due to: %s.", resource.getName(), e.getMessage());
            throw new RuntimeException(message, e);
        }
    }
}
