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

package org.gradle.nativeplatform.toolchain.internal.gcc;

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
import java.util.Collections;
import java.util.List;

class Assembler implements Compiler<AssembleSpec> {

    private final CommandLineTool commandLineTool;
    private final CommandLineToolInvocation baseInvocation;
    private String outputFileSuffix;

    public Assembler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, String outputFileSuffix) {
        this.commandLineTool = commandLineTool;
        this.baseInvocation = baseInvocation;
        this.outputFileSuffix = outputFileSuffix;
    }

    public WorkResult execute(AssembleSpec spec) {
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.setWorkDirectory(spec.getObjectFileDir());
        for (File sourceFile : spec.getSourceFiles()) {
            ArgsTransformer<AssembleSpec> arguments = new AssembleSpecToArgsList(sourceFile, spec.getObjectFileDir(), outputFileSuffix);
            invocation.setArgs(arguments.transform(spec));
            commandLineTool.execute(invocation);
        }
        return new SimpleWorkResult(!spec.getSourceFiles().isEmpty());
    }

    private static class AssembleSpecToArgsList implements ArgsTransformer<AssembleSpec> {
        private final File inputFile;
        private final File outputFile;

        public AssembleSpecToArgsList(File inputFile, File objectFileRootDir, String outputFileSuffix) {
            this.inputFile = inputFile;
            this.outputFile = new CompilerOutputFileNamingScheme()
                                    .withOutputBaseFolder(objectFileRootDir)
                                    .withObjectFileNameSuffix(outputFileSuffix)
                                    .map(inputFile);
        }

        public List<String> transform(AssembleSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(spec.getAllArgs());
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            Collections.addAll(args, "-o", outputFile.getAbsolutePath());
            args.add(inputFile.getAbsolutePath());
            return args;
        }
    }
}
