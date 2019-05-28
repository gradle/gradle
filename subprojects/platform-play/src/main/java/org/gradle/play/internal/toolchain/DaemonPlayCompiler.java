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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.HierarchicalClassLoaderStructure;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;

public class DaemonPlayCompiler<T extends PlayCompileSpec> extends AbstractDaemonCompiler<T> {
    private final Iterable<File> compilerClasspath;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final File daemonWorkingDir;

    public DaemonPlayCompiler(File daemonWorkingDir, Class<? extends Compiler<T>> compiler, Object[] compilerParameters, WorkerDaemonFactory workerDaemonFactory, Iterable<File> compilerClasspath, JavaForkOptionsFactory forkOptionsFactory, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry) {
        super(compiler, compilerParameters, workerDaemonFactory);
        this.compilerClasspath = compilerClasspath;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(PlayCompileSpec spec) {
        BaseForkOptions forkOptions = spec.getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(forkOptions);
        javaForkOptions.setWorkingDir(daemonWorkingDir);

        ClassPath playCompilerClasspath = classPathRegistry.getClassPath("PLAY-COMPILER").plus(DefaultClassPath.of(compilerClasspath));

        HierarchicalClassLoaderStructure classLoaderStructure = new HierarchicalClassLoaderStructure(classLoaderRegistry.getGradleWorkerExtensionSpec())
                .withChild(getPlayFilterSpec())
                .withChild(new VisitableURLClassLoader.Spec("compiler", playCompilerClasspath.getAsURLs()));

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStructure(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();
    }

    private FilteringClassLoader.Spec getPlayFilterSpec() {
        FilteringClassLoader.Spec gradleApiAndPlaySpec = classLoaderRegistry.getGradleApiFilterSpec();

        // These should come from the compiler classloader
        gradleApiAndPlaySpec.disallowPackage("org.gradle.play");

        // Guava
        gradleApiAndPlaySpec.allowPackage("com.google");

        // Apache commons
        gradleApiAndPlaySpec.allowPackage("org.apache.commons.lang");

        return gradleApiAndPlaySpec;
    }
}
