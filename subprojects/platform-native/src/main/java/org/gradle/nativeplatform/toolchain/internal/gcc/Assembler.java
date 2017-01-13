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

import org.gradle.internal.Transformers;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;

import java.util.List;

class Assembler extends GccCompatibleNativeCompiler<AssembleSpec> {

    Assembler(BuildOperationProcessor buildOperationProcessor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineTool, CommandLineToolContext invocationContext, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, invocationContext, new AssemblerArgsTransformer(), Transformers.<AssembleSpec>noOpTransformer(), objectFileExtension, useCommandFile);
    }

    @Override
    protected Iterable<String> buildPerFileArgs(List<String> genericArgs, List<String> sourceArgs, List<String> outputArgs, List<String> pchArgs) {
        if (pchArgs != null && !pchArgs.isEmpty()) {
            throw new UnsupportedOperationException("Precompiled header arguments cannot be specified for an Assembler compiler.");
        }
        return super.buildPerFileArgs(genericArgs, sourceArgs, outputArgs, pchArgs);
    }

    private static class AssemblerArgsTransformer  extends GccCompilerArgsTransformer<AssembleSpec> {
        @Override
        protected String getLanguage() {
            return "assembler";
        }
    }
}
