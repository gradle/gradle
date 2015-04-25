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

package org.gradle.api.internal.tasks.mirah;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.tasks.mirah.MirahCompileOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

import java.io.File;
import java.util.Set;

public class MirahCompilerFactory implements CompilerFactory<MirahJavaJointCompileSpec> {
    private final IsolatedAntBuilder antBuilder;
    private final JavaCompilerFactory javaCompilerFactory;
    private final CompilerDaemonFactory compilerDaemonFactory;
    private FileCollection mirahClasspath;
    private final File rootProjectDirectory;

    public MirahCompilerFactory(File rootProjectDirectory, IsolatedAntBuilder antBuilder, JavaCompilerFactory javaCompilerFactory, CompilerDaemonFactory compilerDaemonFactory, FileCollection mirahClasspath) {
        this.rootProjectDirectory = rootProjectDirectory;
        this.antBuilder = antBuilder;
        this.javaCompilerFactory = javaCompilerFactory;
        this.compilerDaemonFactory = compilerDaemonFactory;
        this.mirahClasspath = mirahClasspath;
    }

    @SuppressWarnings("unchecked")
    public Compiler<MirahJavaJointCompileSpec> newCompiler(MirahJavaJointCompileSpec spec) {
        MirahCompileOptions mirahOptions = (MirahCompileOptions) spec.getMirahCompileOptions();
        Set<File> mirahClasspathFiles = mirahClasspath.getFiles();
        if (mirahOptions.isUseAnt()) {
            Compiler<MirahCompileSpec> mirahCompiler = new AntMirahCompiler(antBuilder, mirahClasspathFiles);
            Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(spec.getClass());
            return new NormalizingMirahCompiler(new DefaultMirahJavaJointCompiler(mirahCompiler, javaCompiler));
        }

        if (!mirahOptions.isFork()) {
            throw new GradleException("The Zinc based Mirah compiler ('mirahCompileOptions.useAnt=false') "
                    + "requires forking ('mirahCompileOptions.fork=true'), but the latter is set to 'false'.");
        }

        // currently, we leave it to ZincMirahCompiler to also compile the Java code
        Compiler<MirahJavaJointCompileSpec> mirahCompiler = new DaemonMirahCompiler<MirahJavaJointCompileSpec>(rootProjectDirectory, new ZincMirahCompiler(mirahClasspathFiles), compilerDaemonFactory, mirahClasspathFiles);
        return new NormalizingMirahCompiler(mirahCompiler);
    }
}
