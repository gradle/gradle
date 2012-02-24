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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A C++ compiler that uses an option file to pass command-line options to the compiler.
 */
public abstract class OptionFileCommandLineCppCompiler extends CommandLineCppCompiler {
    protected OptionFileCommandLineCppCompiler(FileResolver fileResolver) {
        super(fileResolver);
    }

    @Override
    protected void configure(ExecAction compiler, GppCompileSpec spec) {
        File optionsFile = new File(spec.getWorkDir(), "compiler-options.txt");
        try {
            PrintWriter writer = new PrintWriter(optionsFile);
            try {
                writeOptions(spec, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write compiler options file '%s'.", optionsFile.getAbsolutePath()), e);
        }

        compiler.args("@" + optionsFile.getAbsolutePath());
    }

    protected abstract void writeOptions(GppCompileSpec spec, PrintWriter writer);
}
