/*
 * Copyright 2014 the original author or authors.
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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.OptionsFileArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.SingleSourceCompileArgTransformer;
import org.gradle.nativebinaries.toolchain.internal.gcc.ShortCircuitArgsTransformer;

import java.io.File;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements org.gradle.api.internal.tasks.compile.Compiler<T> {

    private final CommandLineTool<T> commandLineTool;
    private final OptionsFileArgsTransformer<T> argsTransFormer;

    NativeCompiler(CommandLineTool<T> commandLineTool, ArgsTransformer<T> argsTransFormer) {
        this.argsTransFormer = new OptionsFileArgsTransformer<T>(
                        ArgWriter.windowsStyleFactory(),
                        argsTransFormer);
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(T spec) {
        boolean didWork = false;
        for (File sourceFile : spec.getSourceFiles()) {
            String objectFileName = FilenameUtils.removeExtension(sourceFile.getName()) + ".obj";
            WorkResult result = commandLineTool.inWorkDirectory(spec.getObjectFileDir())
                    .withArguments(new SingleSourceCompileArgTransformer<T>(sourceFile,
                                                                            objectFileName,
                                                                            new ShortCircuitArgsTransformer<T>(argsTransFormer),
                                                                            true,
                                                                            true))
                    .execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }
}
