/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.cpp.compiler.internal;

import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.plugins.binaries.model.BinaryCompileSpec;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class CommandLineCppCompilerArgumentsToOptionFile<T extends BinaryCompileSpec> implements CompileSpecToArguments<T> {

    private final Transformer<ArgWriter, PrintWriter> argWriterFactory;
    private final CompileSpecToArguments<T> toArguments;

    public CommandLineCppCompilerArgumentsToOptionFile(Transformer<ArgWriter, PrintWriter> argWriterFactory, CompileSpecToArguments<T> toArguments) {
        this.argWriterFactory = argWriterFactory;
        this.toArguments = toArguments;
    }

    public void collectArguments(T spec, ArgCollector collector) {
        File optionsFile = new File(spec.getWorkDir(), "compiler-options.txt");
        try {
            PrintWriter writer = new PrintWriter(optionsFile);
            ArgWriter argWriter = argWriterFactory.transform(writer);
            try {
                toArguments.collectArguments(spec, argWriter);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write compiler options file '%s'.", optionsFile.getAbsolutePath()), e);
        }

        collector.args(String.format("@%s", optionsFile.getAbsolutePath()));
    }
}
