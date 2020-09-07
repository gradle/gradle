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
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.HierarchicalClassLoaderStructure;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;

public class DaemonPlayCompiler<T extends PlayCompileSpec> extends AbstractDaemonCompiler<T> {
    private final Class<? extends Compiler<T>> compilerClass;
    private final Object[] compilerConstructorArguments;
    private final Iterable<File> compilerClasspath;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final File daemonWorkingDir;

    public DaemonPlayCompiler(File daemonWorkingDir, Class<? extends Compiler<T>> compilerClass, Object[] compilerConstructorArguments, WorkerDaemonFactory workerDaemonFactory, Iterable<File> compilerClasspath, JavaForkOptionsFactory forkOptionsFactory, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        super(workerDaemonFactory, actionExecutionSpecFactory);
        this.compilerClass = compilerClass;
        this.compilerConstructorArguments = compilerConstructorArguments;
        this.compilerClasspath = compilerClasspath;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
    }

    @Override
    protected CompilerParameters getCompilerParameters(T spec) {
        return new PlayCompilerParameters<T>(compilerClass.getName(), compilerConstructorArguments, spec);
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(PlayCompileSpec spec) {
        BaseForkOptions forkOptions = spec.getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(forkOptions);
        javaForkOptions.setWorkingDir(daemonWorkingDir);
        javaForkOptions.setExecutable(Jvm.current().getJavaExecutable());

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

    public static class PlayCompilerParameters<T extends PlayCompileSpec> extends CompilerParameters {
        private final T compileSpec;

        public PlayCompilerParameters(String compilerClassName, Object[] compilerInstanceParameters, T compileSpec) {
            super(compilerClassName, compilerInstanceParameters);
            this.compileSpec = compileSpec;
        }

        @Override
        public T getCompileSpec() {
            return compileSpec;
        }
    }
}
