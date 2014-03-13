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
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.OptionsFileArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativebinaries.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class LibExeStaticLibraryArchiver implements Compiler<StaticLibraryArchiverSpec> {
    private final CommandLineTool<StaticLibraryArchiverSpec> commandLineTool;

    public LibExeStaticLibraryArchiver(CommandLineTool<StaticLibraryArchiverSpec> commandLineTool) {
        this.commandLineTool = commandLineTool
                .withArguments(new OptionsFileArgsTransformer<StaticLibraryArchiverSpec>(ArgWriter.windowsStyleFactory(), new LibExeSpecToArguments()
        ));
    }

    public WorkResult execute(StaticLibraryArchiverSpec spec) {
        return commandLineTool.execute(spec);
    }

    private static class LibExeSpecToArguments implements ArgsTransformer<StaticLibraryArchiverSpec> {
        public List<String> transform(StaticLibraryArchiverSpec spec) {
            List<String> args = new ArrayList<String>();
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath());
            args.add("/NOLOGO");
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
