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

package org.gradle.plugins.cpp.msvcpp.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.plugins.binaries.model.LibraryCompileSpec;
import org.gradle.plugins.cpp.compiler.internal.ArgWriter;
import org.gradle.plugins.cpp.compiler.internal.OptionFileCommandLineCppCompiler;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;

import java.io.File;
import java.io.PrintWriter;

class VisualCppCompiler extends OptionFileCommandLineCppCompiler {
    static final String EXECUTABLE = "cl.exe";

    VisualCppCompiler(FileResolver fileResolver) {
        super(fileResolver);
    }

    @Override
    protected String getExecutable() {
        return EXECUTABLE;
    }

    @Override
    protected void writeOptions(GppCompileSpec spec, PrintWriter writer) {
        ArgWriter argsWriter = ArgWriter.windowsStyle(writer);
        argsWriter.args("/nologo");
        argsWriter.args("/EHsc");
        argsWriter.args("/Fe" + spec.getOutputFile().getAbsolutePath());
        if (spec instanceof LibraryCompileSpec) {
            argsWriter.args("/LD");
        }
        for (File file : spec.getIncludeRoots()) {
            argsWriter.args("/I", file.getAbsolutePath());
        }
        for (File file : spec.getSource()) {
            argsWriter.args(file);
        }
        // Link options need to be on one line in the options file
        for (File file : spec.getLibs()) {
            argsWriter.args("/link", file.getAbsolutePath().replaceFirst("\\.dll$", ".lib"));
        }
    }
}
