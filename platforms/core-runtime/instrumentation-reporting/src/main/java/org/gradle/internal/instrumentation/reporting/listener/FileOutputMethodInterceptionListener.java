/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.reporting.listener;

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class FileOutputMethodInterceptionListener implements MethodInterceptionListener, AutoCloseable {

    private final OnInterceptedMethodInsFormatter formatter;
    private final Writer writer;
    private final File source;

    public FileOutputMethodInterceptionListener(@Nullable File source, File output) {
        this.source = source;
        try {
            this.writer = new OutputStreamWriter(Files.newOutputStream(output.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.formatter = new OnInterceptedMethodInsFormatter();
    }

    @Override
    public void onInterceptedMethodInstruction(BytecodeInterceptorType type, String sourceFileName, String relativePath, String owner, String name, String descriptor, int lineNumber) {
        try {
            writer.write(formatter.format(this.source, sourceFileName, relativePath, owner, name, descriptor, lineNumber) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
