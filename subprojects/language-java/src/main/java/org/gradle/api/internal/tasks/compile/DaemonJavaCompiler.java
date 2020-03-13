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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.FlatClassLoaderStructure;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DaemonJavaCompiler extends AbstractDaemonCompiler<JavaCompileSpec> {
    private final Class<? extends Compiler<JavaCompileSpec>> compilerClass;
    private final Object[] compilerConstructorArguments;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final File daemonWorkingDir;
    private final ClassPathRegistry classPathRegistry;

    public DaemonJavaCompiler(File daemonWorkingDir, Class<? extends Compiler<JavaCompileSpec>> compilerClass, Object[] compilerConstructorArguments, WorkerDaemonFactory workerDaemonFactory, JavaForkOptionsFactory forkOptionsFactory, ClassPathRegistry classPathRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        super(workerDaemonFactory, actionExecutionSpecFactory);
        this.compilerClass = compilerClass;
        this.compilerConstructorArguments = compilerConstructorArguments;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.classPathRegistry = classPathRegistry;
    }

    @Override
    protected CompilerParameters getCompilerParameters(JavaCompileSpec spec) {
        return new JavaCompilerParameters(compilerClass.getName(), compilerConstructorArguments, spec);
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(JavaCompileSpec spec) {
        ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(forkOptions);
        javaForkOptions.setWorkingDir(daemonWorkingDir);
        JavaInfo jvm = findJvm(javaForkOptions);
        ClassPath compilerClasspath = classPathRegistry.getClassPath("JAVA-COMPILER");
        if (jvm != null && jvm.getToolsJar() != null) {
            List<File> classPath = new ArrayList<>(compilerClasspath.getAsFiles());
            classPath.add(jvm.getToolsJar());
            compilerClasspath = DefaultClassPath.of(classPath);
        }
        FlatClassLoaderStructure classLoaderStructure = new FlatClassLoaderStructure(new VisitableURLClassLoader.Spec("compiler", compilerClasspath.getAsURLs()));

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStructure(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();
    }

    @Nullable
    private JavaInfo findJvm(JavaForkOptions forkOptions) {
        JavaInfo jvm = null;
        String compilerExec = forkOptions.getExecutable();
        if (compilerExec != null) {
            File cur = new File(compilerExec);
            if (cur.exists()) { // .../bin/javac
                cur = cur.getParentFile(); // ..../bin
                if (cur.exists()) {
                    cur = cur.getParentFile(); // home, sweet Java home!
                }
            }
            if (cur.exists()) {
                jvm = Jvm.forHome(cur);
            }
        }
        return jvm;
    }

    public static class JavaCompilerParameters extends CompilerParameters {
        private final JavaCompileSpec compileSpec;

        public JavaCompilerParameters(String compilerClassName, Object[] compilerInstanceParameters, JavaCompileSpec compileSpec) {
            super(compilerClassName, compilerInstanceParameters);
            this.compileSpec = compileSpec;
        }

        @Override
        public JavaCompileSpec getCompileSpec() {
            return compileSpec;
        }
    }
}
