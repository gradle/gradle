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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.ShortCircuitArgsTransformer;

import java.io.File;
import java.util.Arrays;
import java.util.List;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<T> argsTransFormer;
    private final Transformer<T, T> specTransformer;
    private final CommandLineToolInvocation baseInvocation;

    NativeCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation invocation, ArgsTransformer<T> argsTransFormer, Transformer<T, T> specTransformer) {
        this.argsTransFormer = argsTransFormer;
        this.commandLineTool = commandLineTool;
        this.baseInvocation = invocation;
        this.specTransformer = specTransformer;
    }

    public WorkResult execute(T spec) {
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.addPostArgsAction(new VisualCppOptionsFileArgTransformer(spec.getTempDir()));

        Transformer<List<String>, File> outputFileArgTransformer = new Transformer<List<String>, File>(){
            public List<String> transform(File outputFile) {
                return Arrays.asList("/Fo"+ outputFile.getAbsolutePath());
            }
        };
        for (File sourceFile : spec.getSourceFiles()) {
            String objectFileNameSuffix = ".obj";
            SingleSourceCompileArgTransformer<T> argTransformer = new SingleSourceCompileArgTransformer<T>(sourceFile,
                    objectFileNameSuffix,
                    new ShortCircuitArgsTransformer<T>(argsTransFormer),
                    true,
                    outputFileArgTransformer);
            invocation.setArgs(argTransformer.transform(specTransformer.transform(spec)));
            invocation.setWorkDirectory(spec.getObjectFileDir());
            commandLineTool.execute(invocation);
        }
        return new SimpleWorkResult(!spec.getSourceFiles().isEmpty());
    }
}
