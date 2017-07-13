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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

public class ScalaCompilerFactory implements CompilerFactory<ScalaJavaJointCompileSpec> {
    private final WorkerDaemonFactory workerDaemonFactory;
    private FileCollection scalaClasspath;
    private FileCollection zincClasspath;
    private final File rootProjectDirectory;
    private final File gradleUserHomeDir;
    private final FileResolver fileResolver;

    public ScalaCompilerFactory(
        File rootProjectDirectory, WorkerDaemonFactory workerDaemonFactory, FileCollection scalaClasspath,
        FileCollection zincClasspath, File gradleUserHomeDir, FileResolver fileResolver) {
        this.rootProjectDirectory = rootProjectDirectory;
        this.workerDaemonFactory = workerDaemonFactory;
        this.scalaClasspath = scalaClasspath;
        this.zincClasspath = zincClasspath;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.fileResolver = fileResolver;
    }

    public Compiler<ScalaJavaJointCompileSpec> newCompiler(ScalaJavaJointCompileSpec spec) {
        Set<File> scalaClasspathFiles = scalaClasspath.getFiles();
        Set<File> zincClasspathFiles = zincClasspath.getFiles();

        // currently, we leave it to ZincScalaCompiler to also compile the Java code
        Compiler<ScalaJavaJointCompileSpec> scalaCompiler = new DaemonScalaCompiler<ScalaJavaJointCompileSpec>(
            rootProjectDirectory, new ZincScalaCompiler(scalaClasspathFiles, zincClasspathFiles, gradleUserHomeDir),
            workerDaemonFactory, zincClasspathFiles, fileResolver);
        return new NormalizingScalaCompiler(scalaCompiler);
    }
}
