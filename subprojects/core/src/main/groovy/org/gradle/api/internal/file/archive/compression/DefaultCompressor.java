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

import org.apache.tools.bzip2.CBZip2OutputStream;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.CompressionAware;
import org.gradle.api.tasks.bundling.Compressor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * by Szczepan Faber, created at: 11/17/11
 */
public class DefaultCompressor implements Compressor, CompressionAware {

    private final Compression compression;

    public DefaultCompressor(Compression compression) {
        this.compression = compression;
    }

    public OutputStream compress(File destination) {
        try {
            OutputStream outStr = new FileOutputStream(destination);
            switch (compression) {
                case GZIP:
                    return new GZIPOutputStream(outStr);
                case BZIP2:
                    outStr.write('B');
                    outStr.write('Z');
                    return new CBZip2OutputStream(outStr);
                default:
                    return outStr;
            }
        } catch (Exception e) {
            String message = String.format("Unable to create output stream for file: %s due to: %s ", destination, e.getMessage());
            throw new RuntimeException(message, e);
        }
    }

    public Compression getCompression() {
        return compression;
    }
}
