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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

public class ScalaCompilerFactory implements CompilerFactory<ScalaJavaJointCompileSpec> {
    private final WorkerDaemonFactory workerDaemonFactory;
    private FileCollection scalaClasspath;
    private FileCollection zincClasspath;
    private final File daemonWorkingDir;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final ClasspathHasher classpathHasher;

    public ScalaCompilerFactory(
        File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory, FileCollection scalaClasspath,
        FileCollection zincClasspath, JavaForkOptionsFactory forkOptionsFactory,
        ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory,
        ClasspathHasher classpathHasher) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.scalaClasspath = scalaClasspath;
        this.zincClasspath = zincClasspath;
        this.forkOptionsFactory = forkOptionsFactory;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.classpathHasher = classpathHasher;
    }

    @Override
    public Compiler<ScalaJavaJointCompileSpec> newCompiler(ScalaJavaJointCompileSpec spec) {
        Set<File> scalaClasspathFiles = scalaClasspath.getFiles();
        Set<File> zincClasspathFiles = zincClasspath.getFiles();

        HashedClasspath hashedScalaClasspath = new HashedClasspath(DefaultClassPath.of(scalaClasspathFiles), classpathHasher);

        // currently, we leave it to ZincScalaCompiler to also compile the Java code
        Compiler<ScalaJavaJointCompileSpec> scalaCompiler = new DaemonScalaCompiler<ScalaJavaJointCompileSpec>(
            daemonWorkingDir, ZincScalaCompilerFacade.class, new Object[] {hashedScalaClasspath, false},
            workerDaemonFactory, zincClasspathFiles, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory);
        return new NormalizingScalaCompiler(scalaCompiler);
    }
}
