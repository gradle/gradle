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

package org.gradle.play.internal.toolchain;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;

public class DaemonPlayCompiler<T extends PlayCompileSpec> extends AbstractDaemonCompiler<T> {
    private final Iterable<File> compilerClasspath;
    private final Iterable<String> classLoaderPackages;
    private final FileResolver fileResolver;
    private final File daemonWorkingDir;

    public DaemonPlayCompiler(File daemonWorkingDir, Compiler<T> compiler, WorkerDaemonFactory workerDaemonFactory, Iterable<File> compilerClasspath, Iterable<String> classLoaderPackages, FileResolver fileResolver) {
        super(compiler, workerDaemonFactory);
        this.compilerClasspath = compilerClasspath;
        this.classLoaderPackages = classLoaderPackages;
        this.fileResolver = fileResolver;
        this.daemonWorkingDir = daemonWorkingDir;
    }

    @Override
    protected InvocationContext toInvocationContext(PlayCompileSpec spec) {
        BaseForkOptions forkOptions = spec.getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(fileResolver).transform(forkOptions);
        File invocationWorkingDir = javaForkOptions.getWorkingDir();
        javaForkOptions.setWorkingDir(daemonWorkingDir);

        DaemonForkOptions daemonForkOptions = new DaemonForkOptionsBuilder(fileResolver)
            .javaForkOptions(javaForkOptions)
            .classpath(compilerClasspath)
            .sharedPackages(classLoaderPackages)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();

        return new InvocationContext(invocationWorkingDir, daemonForkOptions);
    }
}
