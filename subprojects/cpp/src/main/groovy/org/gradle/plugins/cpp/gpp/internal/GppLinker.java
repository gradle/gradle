/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompiler;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerArgumentsToOptionFile;
import org.gradle.plugins.cpp.internal.LinkerSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

class GppLinker extends CommandLineCppCompiler<LinkerSpec> {

    protected GppLinker(File executable, Factory<ExecAction> execActionFactory, boolean useCommandFile, boolean createSharedLibrary) {
        super(executable, execActionFactory, useCommandFile ? viaCommandFile(createSharedLibrary) : withoutCommandFile(createSharedLibrary));
    }

    private static GppLinkerSpecToArguments withoutCommandFile(boolean createSharedLibrary) {
        return new GppLinkerSpecToArguments(createSharedLibrary);
    }

    private static CommandLineCppCompilerArgumentsToOptionFile<LinkerSpec> viaCommandFile(boolean createSharedLibrary) {
        return new CommandLineCppCompilerArgumentsToOptionFile<LinkerSpec>(
            ArgWriter.unixStyleFactory(), new GppLinkerSpecToArguments(createSharedLibrary)
        );
    }

    private static class GppLinkerSpecToArguments implements CompileSpecToArguments<LinkerSpec> {
        private final boolean createSharedLibrary;

        public GppLinkerSpecToArguments(boolean createSharedLibrary) {
            this.createSharedLibrary = createSharedLibrary;
        }

        public void collectArguments(LinkerSpec spec, ArgCollector collector) {
            collector.args("-o", spec.getOutputFile().getAbsolutePath());
            if (createSharedLibrary) {
                collector.args("-shared");
                if (!OperatingSystem.current().isWindows()) {
                    if (OperatingSystem.current().isMacOsX()) {
                        collector.args("-Wl,-install_name," + spec.getInstallName());
                    } else {
                        collector.args("-Wl,-soname," + spec.getInstallName());
                    }
                }
            }
            for (File file : spec.getObjectFiles()) {
                collector.args(file.getAbsolutePath());
            }
            for (File file : spec.getLibs()) {
                collector.args(file.getAbsolutePath());
            }
            collector.args(spec.getArgs());
        }
    }
}
