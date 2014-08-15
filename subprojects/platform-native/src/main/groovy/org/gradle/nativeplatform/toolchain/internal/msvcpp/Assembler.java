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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineTool;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolInvocation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class Assembler implements Compiler<AssembleSpec> {

    private final CommandLineTool commandLineTool;
    private final CommandLineToolInvocation baseInvocation;

    public Assembler(CommandLineTool commandLineTool, CommandLineToolInvocation invocation) {
        this.commandLineTool = commandLineTool;
        this.baseInvocation = invocation;
    }

    public WorkResult execute(AssembleSpec spec) {
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.setWorkDirectory(spec.getObjectFileDir());
        for (File sourceFile : spec.getSourceFiles()) {
            invocation.setArgs(new AssemblerArgsTransformer(sourceFile).transform(spec));
            commandLineTool.execute(invocation);
        }
        return new SimpleWorkResult(!spec.getSourceFiles().isEmpty());
    }


    private static class AssemblerArgsTransformer implements ArgsTransformer<AssembleSpec> {
        private final File inputFile;

        public AssemblerArgsTransformer(File inputFile) {
            this.inputFile = inputFile;
        }

        public List<String> transform(AssembleSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            args.add("/nologo");
            args.add("/c");
            File outputFile = new CompilerOutputFileNamingScheme()
                    .withOutputBaseFolder(spec.getObjectFileDir())
                    .withObjectFileNameSuffix(".obj")
                    .map(inputFile);

            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            args.add("/Fo" + outputFile);
            args.add(inputFile.getAbsolutePath());
            return args;
        }
    }
}
