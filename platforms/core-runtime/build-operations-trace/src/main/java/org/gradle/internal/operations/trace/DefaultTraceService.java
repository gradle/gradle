/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.operations.trace;

import androidx.tracing.driver.ProcessTrack;
import androidx.tracing.driver.ThreadTrack;
import androidx.tracing.driver.TraceContext;
import androidx.tracing.driver.TraceDriver;
import androidx.tracing.driver.wire.WireTraceSink_jvmKt;
import kotlinx.coroutines.Dispatchers;
import org.gradle.internal.operations.TraceService;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public class DefaultTraceService implements TraceService, Closeable {
    private final TraceContext context;
    private final ProcessTrack processTrack;
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<ThreadTrack> threadTrack = new ThreadLocal<ThreadTrack>() {
        @Override
        protected ThreadTrack initialValue() {
            return processTrack.getOrCreateThreadTrack((int) Thread.currentThread().getId(), Thread.currentThread().getName());
        }
    };

    public DefaultTraceService() {
        File directory = new File("/tmp/gradle-trace");
        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TraceDriver driver = new TraceDriver(WireTraceSink_jvmKt.WireTraceSink(directory, 1, Dispatchers.getIO()), true);
        context = driver.getContext();

        processTrack = context.getOrCreateProcessTrack(1, "gradle");
    }

    @Override
    public void beginTrace(String operationName) {
        threadTrack.get().beginSection(operationName);
    }

    @Override
    public void endTrace() {
        threadTrack.get().endSection();
    }

    @Override
    public void close() {
        context.close();
    }
}
