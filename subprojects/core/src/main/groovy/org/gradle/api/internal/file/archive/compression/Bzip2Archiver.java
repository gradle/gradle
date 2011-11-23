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

import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.tools.bzip2.CBZip2OutputStream;
import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.CompressionAware;

import java.io.*;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class Bzip2Archiver implements ReadableResource, Archiver, CompressionAware {

    private DescribedReadableResource resource;

    public Bzip2Archiver() {
        //TODO SF refactor
    }

    public Bzip2Archiver(DescribedReadableResource resource) {
        this.resource = resource;
    }

    public InputStream decompress(File source) {
        try {
            FileInputStream fileInputStream = new FileInputStream(source);
            InputStream is = new BufferedInputStream(fileInputStream);
            // CBZip2InputStream expects the opening "BZ" to be skipped
            byte[] skip = new byte[2];
            is.read(skip);
            return new CBZip2InputStream(is);
        } catch (Exception e) {
            String message = String.format("Unable to create bzip2 input stream for file: %s due to: %s.", source.getName(), e.getMessage());
            throw new RuntimeException(message, e);
        }
    }

    public OutputStream compress(File destination) {
        try {
            OutputStream outStr = new FileOutputStream(destination);
            outStr.write('B');
            outStr.write('Z');
            return new CBZip2OutputStream(outStr);
        } catch (Exception e) {
            String message = String.format("Unable to create bzip2 output stream for file: %s due to: %s ", destination, e.getMessage());
            throw new RuntimeException(message, e);
        }
    }

    public Compression getCompression() {
        return Compression.BZIP2;
    }

    public InputStream read() {
        InputStream fileInputStream = resource.read();
        try {
            InputStream is = new BufferedInputStream(fileInputStream);
            // CBZip2InputStream expects the opening "BZ" to be skipped
            byte[] skip = new byte[2];
            is.read(skip);
            return new CBZip2InputStream(is);
        } catch (Exception e) {
            String message = String.format("Unable to create bzip2 input stream for resource: %s due to: %s.", resource.getName(), e.getMessage());
            throw new RuntimeException(message, e);
        }
    }
}