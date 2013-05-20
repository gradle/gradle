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

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.LibraryCompileSpec;
import org.gradle.plugins.cpp.internal.CppCompileSpec;

import java.io.File;

public class GppCompileSpecToArguments implements CompileSpecToArguments<CppCompileSpec> {

    public void collectArguments(CppCompileSpec spec, ArgCollector collector) {
        collector.args(spec.getArgs());
        collector.args("-o", spec.getOutputFile().getAbsolutePath());
        if (spec instanceof LibraryCompileSpec) {
            LibraryCompileSpec librarySpec = (LibraryCompileSpec) spec;
            collector.args("-shared");
            if (!OperatingSystem.current().isWindows()) {
                collector.args("-fPIC");
                if (OperatingSystem.current().isMacOsX()) {
                    collector.args("-Wl,-install_name," + librarySpec.getInstallName());
                } else {
                    collector.args("-Wl,-soname," + librarySpec.getInstallName());
                }
            }
        }
        for (File file : spec.getIncludeRoots()) {
            collector.args("-I");
            collector.args(file.getAbsolutePath());
        }
        for (File file : spec.getSource()) {
            collector.args(file.getAbsolutePath());
        }
        for (File file : spec.getLibs()) {
            collector.args(file.getAbsolutePath());
        }
    }

}
