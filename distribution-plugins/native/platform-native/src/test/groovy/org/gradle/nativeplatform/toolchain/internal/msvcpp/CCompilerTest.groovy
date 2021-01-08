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

package org.gradle.nativeplatform.toolchain.internal.msvcpp
import org.gradle.internal.Transformers
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.NativeCompiler
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec

class CCompilerTest extends VisualCppNativeCompilerTest {

    @Override
    protected NativeCompiler getCompiler(CommandLineToolContext invocationContext, String objectFileExtension, boolean useCommandFile) {
        new CCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, invocationContext, Transformers.noOpTransformer(), objectFileExtension, useCommandFile, workerLeaseService)
    }

    @Override
    protected Class<CCompileSpec> getCompileSpecType() {
        CCompileSpec
    }

    @Override
    protected List<String> getCompilerSpecificArguments(File includeDir, File systemIncludeDir) {
        [ '/TC' ] + super.getCompilerSpecificArguments(includeDir, systemIncludeDir)
    }
}
