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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Transformer;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsMessageCompileSpec;

import com.google.common.collect.Iterables;

class WindowsMessageCompiler extends VisualCppNativeCompiler<WindowsMessageCompileSpec> {

    WindowsMessageCompiler(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineTool, CommandLineToolContext invocationContext, Transformer<WindowsMessageCompileSpec, WindowsMessageCompileSpec> specTransformer, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationProcessor, commandLineTool, invocationContext, new McCompilerArgsTransformer(), specTransformer, objectFileExtension, useCommandFile);
    }

    @Override
    protected File getOutputFileDir(File sourceFile, File objectFileDir, String fileSuffix) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        File outputFile = sourceFile.getParentFile();
        return windowsPathLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }


    @Override
    protected List<String> getOutputArgs(File outputFile) {
    	List<String> outputArgs = new ArrayList<>();
    	outputArgs.add("/h");outputArgs.add(outputFile.getAbsolutePath());
    	outputArgs.add("/r");outputArgs.add(outputFile.getAbsolutePath());
        return outputArgs;
    }

    
    @Override
    protected Iterable<String> buildPerFileArgs(List<String> genericArgs, List<String> sourceArgs, List<String> outputArgs, List<String> pchArgs) {
        if (pchArgs != null && !pchArgs.isEmpty()) {
            throw new UnsupportedOperationException("Precompiled header arguments cannot be specified for a Windows Message compiler.");
        }
        // RC has position sensitive arguments, the output args need to appear before the source file
        return Iterables.concat(genericArgs, outputArgs, sourceArgs);
    }

    private static class McCompilerArgsTransformer extends VisualCompilerArgsTransformer<WindowsMessageCompileSpec> {
        @Override
        protected String getLanguageOption() {
            return "/r";
        }
    }
}
