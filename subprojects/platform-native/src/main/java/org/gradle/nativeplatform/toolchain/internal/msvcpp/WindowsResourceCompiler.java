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

import com.google.common.collect.Iterables;
import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec;

import java.util.List;

class WindowsResourceCompiler extends VisualCppNativeCompiler<WindowsResourceCompileSpec> {

    WindowsResourceCompiler(BuildOperationProcessor buildOperationProcessor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineTool, CommandLineToolContext invocationContext, Transformer<WindowsResourceCompileSpec, WindowsResourceCompileSpec> specTransformer, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, invocationContext, new RcCompilerArgsTransformer(), specTransformer, objectFileExtension, useCommandFile);
    }

    @Override
    protected Iterable<String> buildPerFileArgs(List<String> genericArgs, List<String> sourceArgs, List<String> outputArgs, List<String> pchArgs) {
        if (pchArgs != null && !pchArgs.isEmpty()) {
            throw new UnsupportedOperationException("Precompiled header arguments cannot be specified for a Windows Resource compiler.");
        }
        // RC has position sensitive arguments, the output args need to appear before the source file
        return Iterables.concat(genericArgs, outputArgs, sourceArgs);
    }

    private static class RcCompilerArgsTransformer extends VisualCppCompilerArgsTransformer<WindowsResourceCompileSpec> {
        @Override
        protected void addToolSpecificArgs(WindowsResourceCompileSpec spec, List<String> args) {
            args.add(getLanguageOption());
            args.add("/nologo");
        }
        @Override
        protected String getLanguageOption() {
            return "/r";
        }
    }
}
