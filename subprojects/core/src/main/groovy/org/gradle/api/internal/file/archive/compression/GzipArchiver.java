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

import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.CompressionAware;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class GzipArchiver implements Archiver, CompressionAware {

    public InputStream decompress(File source) {
        try {
            FileInputStream is = new FileInputStream(source);
            return new GZIPInputStream(is);
        } catch (Exception e) {
            String message = String.format("Unable to create gzip input stream for file: %s due to: %s.", source.getName(), e.getMessage());
            throw new RuntimeException(message, e);
        }
    }

    public OutputStream compress(File destination) {
        try {
            OutputStream outStr = new FileOutputStream(destination);
            return new GZIPOutputStream(outStr);
        } catch (Exception e) {
            String message = String.format("Unable to create gzip output stream for file: %s due to: %s ", destination, e.getMessage());
            throw new RuntimeException(message, e);
        }
    }

    public Compression getCompression() {
        return Compression.GZIP;
    }
}
