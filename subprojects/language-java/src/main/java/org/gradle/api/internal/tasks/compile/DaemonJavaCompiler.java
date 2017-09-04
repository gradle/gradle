/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Collections;

public class DaemonJavaCompiler extends AbstractDaemonCompiler<JavaCompileSpec> {
    private static final Iterable<String> SHARED_PACKAGES = Collections.singleton("com.sun.tools.javac");
    private final FileResolver fileResolver;
    private final File daemonWorkingDir;

    public DaemonJavaCompiler(File daemonWorkingDir, Compiler<JavaCompileSpec> delegate, WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver) {
        super(delegate, workerDaemonFactory);
        this.fileResolver = fileResolver;
        this.daemonWorkingDir = daemonWorkingDir;
    }

    @Override
    protected InvocationContext toInvocationContext(JavaCompileSpec spec) {
        ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(fileResolver).transform(forkOptions);
        File invocationWorkingDir = javaForkOptions.getWorkingDir();
        javaForkOptions.setWorkingDir(daemonWorkingDir);

        DaemonForkOptions daemonForkOptions = new DaemonForkOptionsBuilder(fileResolver)
            .javaForkOptions(javaForkOptions)
            .sharedPackages(SHARED_PACKAGES)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();

        return new InvocationContext(invocationWorkingDir, daemonForkOptions);
    }
}
