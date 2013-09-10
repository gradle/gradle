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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.SharedLibraryLinkerSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineCompilerArgumentsToOptionFile;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;

class GccLinker implements Compiler<LinkerSpec> {

    private final CommandLineTool<LinkerSpec> commandLineTool;

    public GccLinker(CommandLineTool<LinkerSpec> commandLineTool, boolean useCommandFile) {
        this.commandLineTool = commandLineTool.withArguments(useCommandFile ? viaCommandFile() : withoutCommandFile());
    }

    private static GccLinkerSpecToArguments withoutCommandFile() {
        return new GccLinkerSpecToArguments();
    }

    private static CommandLineCompilerArgumentsToOptionFile<LinkerSpec> viaCommandFile() {
        return new CommandLineCompilerArgumentsToOptionFile<LinkerSpec>(
            ArgWriter.unixStyleFactory(), new GccLinkerSpecToArguments()
        );
    }

    public WorkResult execute(LinkerSpec spec) {
        return commandLineTool.execute(spec);
    }

    private static class GccLinkerSpecToArguments implements CompileSpecToArguments<LinkerSpec> {

        public void collectArguments(LinkerSpec spec, ArgCollector collector) {
            for (String rawArg : spec.getArgs()) {
                collector.args("-Xlinker", rawArg);
            }
            if (spec instanceof SharedLibraryLinkerSpec) {
                collector.args("-shared");
                if (!OperatingSystem.current().isWindows()) {
                    String installName = ((SharedLibraryLinkerSpec) spec).getInstallName();
                    if (OperatingSystem.current().isMacOsX()) {
                        collector.args("-Wl,-install_name," + installName);
                    } else {
                        collector.args("-Wl,-soname," + installName);
                    }
                }
            }
            collector.args("-o", spec.getOutputFile().getAbsolutePath());
            for (File file : spec.getSource()) {
                collector.args(file.getAbsolutePath());
            }
            for (File file : spec.getLibs()) {
                collector.args(file.getAbsolutePath());
            }
        }
    }
}
