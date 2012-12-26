/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import java.io.File;
import java.io.IOException;

import org.apache.tools.zip.ZipOutputStream;

public class ZipCompressedCompressor implements ZipCompressor {

    public static final ZipCompressor INSTANCE = new ZipCompressedCompressor();

    public ZipOutputStream compress(File destination) {
        try {
            return new ZipOutputStream(destination);
        } catch (IOException e) {
            String message = String.format("Unable to create zip output stream for file %s.", destination);
            throw new RuntimeException(message, e);
        }
    }

    private ZipCompressedCompressor() {}
}
