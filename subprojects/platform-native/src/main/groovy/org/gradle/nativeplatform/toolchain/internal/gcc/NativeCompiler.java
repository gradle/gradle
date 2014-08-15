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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<T> argsTransfomer;
    private final CommandLineToolInvocation baseInvocation;
    private String objectFileSuffix;
    private final boolean useCommandFile;

    public NativeCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, ArgsTransformer<T> argsTransformer, String objectFileSuffix, boolean useCommandFile) {
        this.baseInvocation = baseInvocation;
        this.objectFileSuffix = objectFileSuffix;
        this.useCommandFile = useCommandFile;
        this.argsTransfomer = argsTransformer;
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(T spec) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.setWorkDirectory(spec.getObjectFileDir());
        if (useCommandFile) {
            invocation.addPostArgsAction(new GccOptionsFileArgTransformer(spec.getTempDir()));
        }

        Transformer<List<String>, File> outputFileArgTransformer = new Transformer<List<String>, File>() {
            public List<String> transform(File outputFile) {
                return Arrays.asList("-o", outputFile.getAbsolutePath());
            }
        };

        for (File sourceFile : spec.getSourceFiles()) {
            SingleSourceCompileArgTransformer<T> argTransformer = new SingleSourceCompileArgTransformer<T>(sourceFile,
                    objectFileSuffix,
                    new ShortCircuitArgsTransformer<T>(argsTransfomer),
                    windowsPathLimitation,
                    outputFileArgTransformer);
            invocation.setArgs(argTransformer.transform(spec));
            commandLineTool.execute(invocation);
        }
        return new SimpleWorkResult(!spec.getSourceFiles().isEmpty());
    }
}
