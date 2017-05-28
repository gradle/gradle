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

import org.apache.commons.io.IOUtils;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.internal.resource.ResourceExceptions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipArchiver extends AbstractArchiver {
    public GzipArchiver(ReadableResourceInternal resource) {
        super(resource);
    }

    protected String getSchemePrefix() {
        return "gzip:";
    }

    public static ArchiveOutputStreamFactory getCompressor() {
        // this is not very beautiful but at some point we will
        // get rid of ArchiveOutputStreamFactory in favor of the writable Resource
        return new ArchiveOutputStreamFactory() {
            public OutputStream createArchiveOutputStream(File destination) throws FileNotFoundException {
                OutputStream outStr = new FileOutputStream(destination);
                try {
                    return new GZIPOutputStream(outStr);
                } catch (Exception e) {
                    IOUtils.closeQuietly(outStr);
                    String message = String.format("Unable to create gzip output stream for file %s.", destination);
                    throw new RuntimeException(message, e);
                }
            }
        };
    }

    public InputStream read() {
        InputStream input = new BufferedInputStream(resource.read());
        try {
            return new GZIPInputStream(input);
        } catch (Exception e) {
            IOUtils.closeQuietly(input);
            throw ResourceExceptions.readFailed(resource.getDisplayName(), e);
        }
    }
}
