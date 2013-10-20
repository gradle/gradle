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

package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.SharedLibraryLinkerSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineCompilerArgumentsToOptionFile;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;

class LinkExeLinker implements Compiler<LinkerSpec> {

    private final CommandLineTool<LinkerSpec> commandLineTool;

    public LinkExeLinker(CommandLineTool<LinkerSpec> commandLineTool) {
        this.commandLineTool = commandLineTool
                .withArguments(new CommandLineCompilerArgumentsToOptionFile<LinkerSpec>(
                ArgWriter.windowsStyleFactory(), new VisualCppLinkerSpecArguments()
        ));
    }

    public WorkResult execute(LinkerSpec spec) {
        return commandLineTool.execute(spec);
    }

    private static class VisualCppLinkerSpecArguments implements CompileSpecToArguments<LinkerSpec> {

        public void collectArguments(LinkerSpec spec, ArgCollector collector) {
            collector.args(spec.getAllArgs());
            collector.args("/OUT:" + spec.getOutputFile().getAbsolutePath());
            collector.args("/NOLOGO");
            if (spec instanceof SharedLibraryLinkerSpec) {
                collector.args("/DLL");
            }
            for (File pathEntry : spec.getLibraryPath()) {
                collector.args("/LIBPATH:" + pathEntry.getAbsolutePath());
            }
            for (File file : spec.getObjectFiles()) {
                collector.args(file.getAbsolutePath());
            }
            for (File file : spec.getLibraries()) {
                collector.args(file.getAbsolutePath());
            }
        }
    }
}
