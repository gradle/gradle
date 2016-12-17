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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;

class CCompiler extends VisualCppNativeCompiler<CCompileSpec> {

    CCompiler(BuildOperationProcessor buildOperationProcessor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, Transformer<CCompileSpec, CCompileSpec> specTransformer, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineToolInvocationWorker, invocationContext, new CCompilerArgsTransformer(), specTransformer, objectFileExtension, useCommandFile);
    }

    private static class CCompilerArgsTransformer extends VisualCppCompilerArgsTransformer<CCompileSpec> {
        @Override
        protected String getLanguageOption() {
            return "/TC";
        }
    }
}
