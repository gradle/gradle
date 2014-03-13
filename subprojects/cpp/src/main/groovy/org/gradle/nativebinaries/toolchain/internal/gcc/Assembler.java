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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.hash.HashUtil;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Assembler implements Compiler<AssembleSpec> {

    private final CommandLineTool<AssembleSpec> commandLineTool;
    private final Action<List<String>> argsAction;
    private String outputFileSuffix;

    public Assembler(CommandLineTool<AssembleSpec> commandLineTool, Action<List<String>> argsAction, String outputFileSuffix) {
        this.commandLineTool = commandLineTool;
        this.argsAction = argsAction;
        this.outputFileSuffix = outputFileSuffix;
    }

    public WorkResult execute(AssembleSpec spec) {
        boolean didWork = false;
        CommandLineTool<AssembleSpec> commandLineAssembler = commandLineTool.inWorkDirectory(spec.getObjectFileDir());
        for (File sourceFile : spec.getSourceFiles()) {
            ArgsTransformer<AssembleSpec> arguments = new AssembleSpecToArgsList(sourceFile, spec.getObjectFileDir(), outputFileSuffix);
            arguments = new PostTransformActionArgsTransformer<AssembleSpec>(arguments, argsAction);
            WorkResult result = commandLineAssembler.withArguments(arguments).execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }

    private static class AssembleSpecToArgsList implements ArgsTransformer<AssembleSpec> {
        private final File inputFile;
        private final File outputFile;

        public AssembleSpecToArgsList(File inputFile, File objectFileRootDir, String outputFileSuffix) {
            this.inputFile = inputFile;
            String outputFileName = FilenameUtils.removeExtension(inputFile.getName())+ outputFileSuffix;
            File currentObjectOutputDir = new File(objectFileRootDir, HashUtil.createCompactMD5(inputFile.getAbsolutePath()));
            this.outputFile = new File(currentObjectOutputDir, outputFileName);
        }

        public List<String> transform(AssembleSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(spec.getAllArgs());
            if(!outputFile.getParentFile().exists()){
                outputFile.getParentFile().mkdir();
            }
            Collections.addAll(args, "-o", outputFile.getAbsolutePath());
            args.add(inputFile.getAbsolutePath());
            return args;
        }
    }
}
