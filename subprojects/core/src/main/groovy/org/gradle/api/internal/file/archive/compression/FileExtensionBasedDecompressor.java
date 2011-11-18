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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.tasks.bundling.Decompressor;

import java.io.File;
import java.io.InputStream;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class FileExtensionBasedDecompressor implements Decompressor {

    private final ArchiverFactory archiverFactory = new ArchiverFactory();

    public InputStream decompress(File source) {
        assert source != null : "source file to decompress cannot be null!";

        String ext = FilenameUtils.getExtension(source.getName());
        Decompressor d = archiverFactory.decompressor(ext);
        return d.decompress(source);
    }
}
