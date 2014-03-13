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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.SingleSourceCompileArgTransformer;

import java.io.File;
import java.util.List;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineTool<T> commandLineTool;
    private final ArgsTransformer<T> argsTransfomer;

    public NativeCompiler(CommandLineTool<T> commandLineTool, Action<List<String>> toolChainArgsAction, ArgsTransformer<T> argsTransformer, boolean useCommandFile) {
        argsTransformer = new PostTransformActionArgsTransformer<T>(argsTransformer, toolChainArgsAction);
        if (useCommandFile) {
            argsTransformer = new GccOptionsFileArgTransformer<T>(argsTransformer);
        }
        this.argsTransfomer = argsTransformer;
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(T spec) {
        boolean didWork = false;
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        String objectFileExtension = OperatingSystem.current().isWindows() ? ".obj" : ".o";
        for (File sourceFile : spec.getSourceFiles()) {
            String objectFileName = FilenameUtils.removeExtension(sourceFile.getName()) + objectFileExtension;
            WorkResult result = commandLineTool.inWorkDirectory(spec.getObjectFileDir())
                    .withArguments(new SingleSourceCompileArgTransformer<T>(sourceFile,
                                            objectFileName,
                                            new ShortCircuitArgsTransformer(argsTransfomer),
                                            windowsPathLimitation,
                                            false))
                    .execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }
}
