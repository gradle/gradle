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
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

public class GppCompiler extends CommandLineCppCompiler {
    static final String EXECUTABLE = "g++";

    public GppCompiler(FileResolver fileResolver) {
        super(fileResolver);
    }

    @Override
    protected String getExecutable() {
        return EXECUTABLE;
    }

    @Override
    protected void configure(ExecAction compiler, GppCompileSpec spec) {
        compiler.args("-o", spec.getOutputFile());
        if (spec instanceof LibraryCompileSpec) {
            LibraryCompileSpec librarySpec = (LibraryCompileSpec) spec;
            compiler.args("-shared");
            if (!OperatingSystem.current().isWindows()) {
                compiler.args("-fPIC");
                if (OperatingSystem.current().isMacOsX()) {
                    compiler.args("-Wl,-install_name," + librarySpec.getInstallName());
                } else {
                    compiler.args("-Wl,-soname," + librarySpec.getInstallName());
                }
            }
        }
        for (File file : spec.getLibs()) {
            compiler.args(file.getAbsolutePath());
        }
    }
}
