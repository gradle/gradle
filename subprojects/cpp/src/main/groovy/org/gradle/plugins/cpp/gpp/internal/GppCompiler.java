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

package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.LibraryCompileSpec;
import org.gradle.plugins.cpp.compiler.internal.ArgWriter;
import org.gradle.plugins.cpp.compiler.internal.OptionFileCommandLineCppCompiler;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;

import java.io.File;
import java.io.PrintWriter;

public class GppCompiler extends OptionFileCommandLineCppCompiler {
    static final String EXECUTABLE = "g++";

    public GppCompiler(FileResolver fileResolver) {
        super(fileResolver);
    }

    @Override
    protected String getExecutable() {
        return EXECUTABLE;
    }

    @Override
    protected void writeOptions(GppCompileSpec spec, PrintWriter w) {
        ArgWriter argWriter = ArgWriter.unixStyle(w);
        argWriter.args("-o", spec.getOutputFile().getAbsolutePath());
        if (spec instanceof LibraryCompileSpec) {
            LibraryCompileSpec librarySpec = (LibraryCompileSpec) spec;
            argWriter.args("-shared");
            if (!OperatingSystem.current().isWindows()) {
                argWriter.args("-fPIC");
                if (OperatingSystem.current().isMacOsX()) {
                    argWriter.args("-Wl,-install_name," + librarySpec.getInstallName());
                } else {
                    argWriter.args("-Wl,-soname," + librarySpec.getInstallName());
                }
            }
        }
        for (File file : spec.getIncludeRoots()) {
            argWriter.args("-I", file.getAbsolutePath());
        }
        for (File file : spec.getSource()) {
            argWriter.args(file.getAbsolutePath());
        }
        for (File file : spec.getLibs()) {
            argWriter.args(file.getAbsolutePath());
        }
    }
}
