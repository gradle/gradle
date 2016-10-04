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

package org.gradle.api.internal.tasks.cache;

import org.gradle.api.internal.TaskOutputsInternal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Adds compression and CRC32 checks to the packed task output.
 */
public class GZipTaskOutputPacker implements TaskOutputPacker {
    private final TarTaskOutputPacker delegate;

    public GZipTaskOutputPacker(TarTaskOutputPacker delegate) {
        this.delegate = delegate;
    }

    @Override
    public void pack(TaskOutputsInternal taskOutputs, OutputStream output) throws IOException {
        GZIPOutputStream gzipOutput = new GZIPOutputStream(output);
        try {
            delegate.pack(taskOutputs, gzipOutput);
        } finally {
            gzipOutput.close();
        }
    }

    @Override
    public void unpack(TaskOutputsInternal taskOutputs, InputStream input) throws IOException {
        GZIPInputStream gzipInput = new GZIPInputStream(input);
        try {
            delegate.unpack(taskOutputs, gzipInput);
        } finally {
            gzipInput.close();
        }
    }
}
