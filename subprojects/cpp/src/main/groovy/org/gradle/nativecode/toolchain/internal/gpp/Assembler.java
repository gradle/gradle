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

package org.gradle.nativecode.toolchain.internal.gpp;

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.nativecode.language.cpp.internal.AssembleSpec;
import org.gradle.nativecode.toolchain.internal.CommandLineTool;
import org.gradle.process.internal.ExecAction;

import java.io.File;

class Assembler implements Compiler<AssembleSpec> {

    private final CommandLineTool<AssembleSpec> commandLineTool;

    public Assembler(File executable, Factory<ExecAction> execActionFactory) {
        this.commandLineTool = new CommandLineTool<AssembleSpec>(executable, execActionFactory);
    }

    public WorkResult execute(AssembleSpec spec) {
        boolean didWork = false;
        CommandLineTool<AssembleSpec> commandLineAssembler = commandLineTool.inWorkDirectory(spec.getObjectFileDir());
        for (File sourceFile : spec.getSource()) {
            WorkResult result = commandLineAssembler.withArguments(new AssemblerSpecToArguments(sourceFile)).execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }


    private static class AssemblerSpecToArguments implements CompileSpecToArguments<AssembleSpec> {
        private final File inputFile;
        private final String outputFileName;

        public AssemblerSpecToArguments(File inputFile) {
            this.inputFile = inputFile;
            this.outputFileName = inputFile.getName() + ".o";
        }

        public void collectArguments(AssembleSpec spec, ArgCollector collector) {
            for (String rawArg : spec.getArgs()) {
                collector.args(rawArg);
            }
            collector.args("-o", outputFileName);
            collector.args(inputFile.getAbsolutePath());
        }
    }
}
