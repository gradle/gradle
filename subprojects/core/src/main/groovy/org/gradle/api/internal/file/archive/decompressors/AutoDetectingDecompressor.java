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

package org.gradle.api.internal.file.archive.decompressors;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Decompressor;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class AutoDetectingDecompressor implements Decompressor {

    private final NoOpDecompressor defaultDecompressor = new NoOpDecompressor();
    private final Map<String, Decompressor> decompressors = new HashMap<String, Decompressor>();

    {{
        decompressors.put("." + Compression.BZIP2.getExtension(), new Bzip2Decompressor());
        decompressors.put("." + Compression.GZIP.getExtension(), new GzipDecompressor());
    }}

    public InputStream decompress(File file) {
        assert file != null : "file to unarchive cannot be null!";

        String ext = "." + FilenameUtils.getExtension(file.getName());

        if (decompressors.containsKey(ext)) {
            return decompressors.get(ext).decompress(file);
        }

        return defaultDecompressor.decompress(file);
    }
}
