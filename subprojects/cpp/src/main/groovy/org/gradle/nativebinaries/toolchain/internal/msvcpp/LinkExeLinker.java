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

import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.SharedLibraryLinkerSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.OptionsFileArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativebinaries.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class LinkExeLinker implements Compiler<LinkerSpec> {

    private final CommandLineTool<LinkerSpec> commandLineTool;

    public LinkExeLinker(CommandLineTool<LinkerSpec> commandLineTool) {
        this.commandLineTool = commandLineTool
                .withArguments(new OptionsFileArgsTransformer<LinkerSpec>(
                ArgWriter.windowsStyleFactory(), new LinkerArgsTransformer()
        ));
    }

    public WorkResult execute(LinkerSpec spec) {
        return commandLineTool.execute(spec);
    }

    private static class LinkerArgsTransformer implements ArgsTransformer<LinkerSpec> {
        public List<String> transform(LinkerSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath());
            args.add("/NOLOGO");
            if (spec instanceof SharedLibraryLinkerSpec) {
                args.add("/DLL");
            }
            for (File pathEntry : spec.getLibraryPath()) {
                args.add("/LIBPATH:" + pathEntry.getAbsolutePath());
            }
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            for (File file : spec.getLibraries()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
