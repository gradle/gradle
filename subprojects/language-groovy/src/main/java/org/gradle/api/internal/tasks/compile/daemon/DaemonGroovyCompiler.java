/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class DaemonGroovyCompiler extends AbstractDaemonCompiler<GroovyJavaJointCompileSpec> {
    private final static Iterable<String> SHARED_PACKAGES = Arrays.asList("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac");
    private final ClassPathRegistry classPathRegistry;
    private final FileResolver fileResolver;
    private final File daemonWorkingDir;

    public DaemonGroovyCompiler(File daemonWorkingDir, Compiler<GroovyJavaJointCompileSpec> delegate, ClassPathRegistry classPathRegistry, WorkerFactory workerFactory, FileResolver fileResolver) {
        super(delegate, workerFactory);
        this.classPathRegistry = classPathRegistry;
        this.fileResolver = fileResolver;
        this.daemonWorkingDir = daemonWorkingDir;
    }

    @Override
    protected InvocationContext toInvocationContext(GroovyJavaJointCompileSpec spec) {
        ForkOptions javaOptions = spec.getCompileOptions().getForkOptions();
        GroovyForkOptions groovyOptions = spec.getGroovyCompileOptions().getForkOptions();
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        Collection<File> antFiles = classPathRegistry.getClassPath("ANT").getAsFiles();
        Iterable<File> groovyFiles = Iterables.concat(spec.getGroovyClasspath(), antFiles);
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(fileResolver).transform(mergeForkOptions(javaOptions, groovyOptions));
        File invocationWorkingDir = javaForkOptions.getWorkingDir();
        javaForkOptions.setWorkingDir(daemonWorkingDir);

        DaemonForkOptions daemonForkOptions = new DaemonForkOptionsBuilder(fileResolver)
            .javaForkOptions(javaForkOptions)
            .classpath(groovyFiles)
            .sharedPackages(SHARED_PACKAGES)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();

        return new InvocationContext(invocationWorkingDir, daemonForkOptions);
    }
}
