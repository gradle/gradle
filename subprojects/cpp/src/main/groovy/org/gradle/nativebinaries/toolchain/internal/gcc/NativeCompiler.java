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
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.*;

import java.io.File;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<T> argsTransfomer;
    private final CommandLineToolInvocation baseInvocation;
    private final boolean useCommandFile;

    public NativeCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, ArgsTransformer<T> argsTransformer, boolean useCommandFile) {
        this.baseInvocation = baseInvocation;
        this.useCommandFile = useCommandFile;
        this.argsTransfomer = argsTransformer;
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(T spec) {
        boolean didWork = false;
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        String objectFileExtension = OperatingSystem.current().isWindows() ? ".obj" : ".o";
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.setWorkDirectory(spec.getObjectFileDir());
        if (useCommandFile) {
            invocation.addPostArgsAction(new GccOptionsFileArgTransformer(spec.getTempDir()));
        }
        for (File sourceFile : spec.getSourceFiles()) {
            String objectFileName = FilenameUtils.removeExtension(sourceFile.getName()) + objectFileExtension;
            SingleSourceCompileArgTransformer<T> argTransformer = new SingleSourceCompileArgTransformer<T>(sourceFile,
                    objectFileName,
                    new ShortCircuitArgsTransformer<T>(argsTransfomer),
                    windowsPathLimitation,
                    false);
            invocation.setArgs(argTransformer.transform(spec));
            WorkResult result = commandLineTool.execute(invocation);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }
}
