/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Adds compression and CRC32 checks to the packed task output.
 */
public class GZipTaskOutputPacker implements TaskOutputPacker {
    private final TaskOutputPacker delegate;

    public GZipTaskOutputPacker(TaskOutputPacker delegate) {
        this.delegate = delegate;
    }

    @Override
    public PackResult pack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, Map<String, FileContentSnapshot>> outputFiles, OutputStream output, TaskOutputOriginWriter writeOrigin) throws IOException {
        GZIPOutputStream gzipOutput = createGzipOutputStream(output);
        try {
            return delegate.pack(propertySpecs, outputFiles, gzipOutput, writeOrigin);
        } finally {
            IOUtils.closeQuietly(gzipOutput);
        }
    }

    private GZIPOutputStream createGzipOutputStream(OutputStream output) {
        try {
            return new GZIPOutputStream(output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public UnpackResult unpack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, InputStream input, TaskOutputOriginReader readOrigin) throws IOException {
        GZIPInputStream gzipInput = createGzipInputStream(input);
        try {
            return delegate.unpack(propertySpecs, gzipInput, readOrigin);
        } finally {
            IOUtils.closeQuietly(gzipInput);
        }
    }

    private GZIPInputStream createGzipInputStream(InputStream input) {
        try {
            return new GZIPInputStream(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
