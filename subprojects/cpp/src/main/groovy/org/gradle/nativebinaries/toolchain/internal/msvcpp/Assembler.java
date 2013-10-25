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

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class Assembler implements Compiler<AssembleSpec> {

    private final CommandLineTool<AssembleSpec> commandLineTool;

    public Assembler(CommandLineTool<AssembleSpec> commandLineTool) {
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(AssembleSpec spec) {
        boolean didWork = false;
        CommandLineTool<AssembleSpec> commandLineAssembler = commandLineTool.inWorkDirectory(spec.getObjectFileDir());
        for (File sourceFile : spec.getSourceFiles()) {
            WorkResult result = commandLineAssembler.withArguments(new AssemblerArgsTransformer(sourceFile)).execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }


    private static class AssemblerArgsTransformer implements ArgsTransformer<AssembleSpec> {
        private final File inputFile;

        public AssemblerArgsTransformer(File inputFile) {
            this.inputFile = inputFile;
        }

        public List<String> transform(AssembleSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(spec.getAllArgs());
            args.add("/nologo");
            args.add("/c");
            args.add(inputFile.getAbsolutePath());
            return args;
        }
    }
}
