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
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineCompilerArgumentsToOptionFile;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.util.List;

class CCompiler implements Compiler<CCompileSpec> {

    private final CommandLineTool<CCompileSpec> commandLineTool;

    CCompiler(CommandLineTool<CCompileSpec> commandLineTool) {
        this.commandLineTool = commandLineTool.withArguments(
                new CommandLineCompilerArgumentsToOptionFile<CCompileSpec>(
                        ArgWriter.windowsStyleFactory(),
                        new CCompileSpecToArguments()));
    }

    public WorkResult execute(CCompileSpec spec) {
        return commandLineTool.inWorkDirectory(spec.getObjectFileDir()).execute(spec);
    }

    private static class CCompileSpecToArguments extends GeneralVisualCppCompileSpecToArguments<CCompileSpec> {
        @Override
        public List<String> transform(CCompileSpec spec) {
            List<String> args = super.transform(spec);
            args.add(0, "/TC");
            return args;
        }
    }
}
