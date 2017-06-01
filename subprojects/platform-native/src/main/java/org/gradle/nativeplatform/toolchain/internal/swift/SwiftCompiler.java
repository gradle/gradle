/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.NativeCompiler;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class SwiftCompiler extends NativeCompiler<SwiftCompileSpec> {

    SwiftCompiler(BuildOperationExecutor buildOperationExecutor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineToolInvocationWorker, invocationContext, new SwiftCompileArgsTransformer(), Transformers.<SwiftCompileSpec>noOpTransformer(), objectFileExtension, useCommandFile);
    }

    @Override
    protected List<String> getOutputArgs(File outputFile) {
        return Arrays.asList("-o", outputFile.getAbsolutePath()/*, "-emit-module-path", outputFile.getAbsolutePath() + ".swiftmodule"*/);
    }

    @Override
    protected List<String> getSourceArgs(File sourceFile) {
        return Arrays.asList("-primary-file", sourceFile.getAbsolutePath());
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
    }

    @Override
    protected List<String> getPCHArgs(SwiftCompileSpec spec) {
        return null;
    }

    @Override
    protected CommandLineToolInvocation createPerFileInvocation(List<String> genericArgs, File sourceFile, File objectDir, SwiftCompileSpec spec) {
        List<String> gen = new ArrayList<String>(genericArgs);
        for (File f : spec.getSourceFiles()) {
            if (!f.equals(sourceFile)) {
                gen.add(f.getAbsolutePath());
            }
        }
        return super.createPerFileInvocation(gen, sourceFile, objectDir, spec);
    }

    private static class SwiftCompileArgsTransformer implements ArgsTransformer<SwiftCompileSpec> {
        @Override
        public List<String> transform(SwiftCompileSpec swiftCompileSpec) {
            return swiftCompileSpec.getArgs();
        }
    }
}
