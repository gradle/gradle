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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Transformer;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;

import java.io.File;
import java.util.List;

public class DummyCompiler extends NativeCompiler<CCompileSpec> {
    private final OptionsFileArgsWriter argsWriter;
    private final OutputFileArgTransformer outputFileArgTransformer;

    DummyCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, ArgsTransformer<CCompileSpec> argsTransformer, Transformer<CCompileSpec, CCompileSpec> specTransformer, OutputFileArgTransformer outputFileArgTransformer, OptionsFileArgsWriter argsWriter) {
        super(commandLineTool, baseInvocation, argsTransformer, specTransformer, ".o", true);
        this.argsWriter = argsWriter;
        this.outputFileArgTransformer = outputFileArgTransformer;
    }

    @Override
    protected OptionsFileArgsWriter optionsFileTransformer(CCompileSpec spec) {
        return argsWriter;
    }

    protected OutputFileArgTransformer outputFileTransformer(File sourceFile, File objectFileDir, String objectFileNameSuffix, boolean windowsPathLengthLimitation) {
        return outputFileArgTransformer;
    }
}
