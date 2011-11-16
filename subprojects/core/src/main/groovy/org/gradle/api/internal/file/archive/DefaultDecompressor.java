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

package org.gradle.api.internal.file.archive;

import org.apache.tools.bzip2.CBZip2InputStream;
import org.gradle.api.tasks.bundling.Decompressor;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class DefaultDecompressor implements Decompressor {

    public InputStream decompress(File input) {
        // Try GZip, BZip2, and plain tar
        try {
            return new GZIPInputStream(inputStream(input));
        } catch (IOException gze) {
            try {
                // CBZip2InputStream expects the opening "Bz" to be skipped
                InputStream is = new BufferedInputStream(inputStream(input));
                byte[] skip = new byte[2];
                is.read(skip);
                return new CBZip2InputStream(is);
            } catch (IOException bze) {
                return inputStream(input);
            }
        }
    }

    private FileInputStream inputStream(File input) {
        try {
            return new FileInputStream(input);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to open file stream for file: %s due to: %s", input, e.getMessage()), e);
        }
    }
}
