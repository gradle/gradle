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

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;
import java.util.List;

public class DaemonTwirlCompiler extends AbstractDaemonCompiler<VersionedTwirlCompileSpec> {
    private final Iterable<File> compilerClasspath;
    private final BaseForkOptions forkOptions;

    public DaemonTwirlCompiler(File projectDir, Iterable<File> compilerClasspath, TwirlCompiler twirlCompiler, CompilerDaemonFactory compilerDaemonFactory, BaseForkOptions forkOptions) {
        super(projectDir, twirlCompiler, compilerDaemonFactory);
        this.compilerClasspath = compilerClasspath;
        this.forkOptions = forkOptions;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected DaemonForkOptions toDaemonOptions(VersionedTwirlCompileSpec spec) {
        List<String> twirlPackages = spec.getClassLoaderPackages();
        return new DaemonForkOptions(forkOptions.getMemoryInitialSize(), forkOptions.getMemoryMaximumSize(), forkOptions.getJvmArgs(), compilerClasspath, twirlPackages);
    }
}
