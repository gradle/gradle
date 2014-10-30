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

package org.gradle.play.internal.twirl;

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

import java.io.File;

public class TwirlCompilerFactory implements CompilerFactory<TwirlCompileSpec> {
    private final File workingDirectory;
    private final CompilerDaemonFactory inProcessCompilerDaemonFactory;

    public TwirlCompilerFactory(File workingDirectory, CompilerDaemonFactory inProcessCompilerDaemonFactory) {
        this.workingDirectory = workingDirectory;
        this.inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
    }

    public org.gradle.language.base.internal.compile.Compiler<TwirlCompileSpec> newCompiler(TwirlCompileSpec spec) {
        Compiler<TwirlCompileSpec> compiler = new DaemonTwirlCompiler(workingDirectory, new TwirlCompiler(new TwirlCompilerVersionedInvocationSpecBuilder()), inProcessCompilerDaemonFactory);
        return compiler;
    }
}
