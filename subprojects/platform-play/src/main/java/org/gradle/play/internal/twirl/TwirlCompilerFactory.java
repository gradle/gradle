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
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.play.internal.twirl.spec.TwirlCompileSpec;

import java.io.File;

public class TwirlCompilerFactory implements CompilerFactory<TwirlCompileSpec> {
    private final File workingDirectory;
    private CompilerDaemonManager compilerDaemonManager;
    private final CompilerDaemonFactory inProcessCompilerDaemonFactory;
    private BaseForkOptions forkOptions;

    public TwirlCompilerFactory(File workingDirectory, CompilerDaemonManager compilerDaemonManager, CompilerDaemonFactory inProcessCompilerDaemonFactory, BaseForkOptions forkOptions) {
        this.workingDirectory = workingDirectory;
        this.compilerDaemonManager = compilerDaemonManager;
        this.inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
        this.forkOptions = forkOptions;
    }

    public org.gradle.language.base.internal.compile.Compiler<TwirlCompileSpec> newCompiler(TwirlCompileSpec spec) {
        CompilerDaemonFactory daemonFactory;
        if (spec.isFork()) {
            daemonFactory = compilerDaemonManager;
        } else {
            daemonFactory = inProcessCompilerDaemonFactory;
        }
        Compiler<TwirlCompileSpec> compiler = new DaemonTwirlCompiler(workingDirectory, new TwirlCompiler(), daemonFactory, forkOptions);
        return compiler;
    }
}
