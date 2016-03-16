/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.gosu;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.tasks.gosu.GosuCompileOptions;
import org.gradle.language.base.internal.compile.*;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GosuCompilerFactory implements CompilerFactory<GosuCompileSpec> {
    private final String taskPath;
    private final IsolatedAntBuilder antBuilder;
    private final CompilerDaemonFactory compilerDaemonFactory;
    private FileCollection gosuClasspath;
    private final File rootProjectDirectory;
    private final File gradleUserHomeDir;

    public GosuCompilerFactory(String taskPath, File rootProjectDirectory, IsolatedAntBuilder antBuilder, CompilerDaemonFactory compilerDaemonFactory, FileCollection gosuClasspath, File gradleUserHomeDir) {
        this.taskPath = taskPath;
        this.rootProjectDirectory = rootProjectDirectory;
        this.antBuilder = antBuilder;
        this.compilerDaemonFactory = compilerDaemonFactory;
        this.gosuClasspath = gosuClasspath;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    @Override
    public Compiler<GosuCompileSpec> newCompiler(GosuCompileSpec spec) {
        GosuCompileOptions gosuCompileOptions = (GosuCompileOptions) spec.getGosuCompileOptions();

        if(gosuCompileOptions.isUseAnt()) {
            if(gosuCompileOptions.isFork()) {
                throw new GradleException("Ant-based Gosu compilation does not support forking. "
                    + "The combination of 'gosuCompileOptions.useAnt=false' and 'gosuCompileOptions.fork=true' is invalid.");
            }
            Compiler<GosuCompileSpec> gosuCompiler = new AntGosuCompiler(antBuilder, spec.getClasspath(), gosuClasspath, taskPath);
            return gosuCompiler;
            //return new NormalizingGosuCompiler(gosuCompiler);
        }

        //TODO:KM FGC constructor cannot take spec.getClasspath() or similar runtime-resolved iterable; must pass a resolved Set<File>
        Set<File> gosuClasspathFiles = gosuClasspath.getFiles();
        Iterable<File> classpath = spec.getClasspath();
        List<File> fullClasspath = new ArrayList<File>();
        fullClasspath.addAll(gosuClasspathFiles);
        for(File file : classpath) {
            fullClasspath.add(file);
        }

        Compiler<GosuCompileSpec> gosuCompiler = new DaemonGosuCompiler<GosuCompileSpec>(rootProjectDirectory, new ForkingGosuCompiler(gosuClasspathFiles, gradleUserHomeDir, taskPath), compilerDaemonFactory, gosuClasspathFiles);
        return new NormalizingGosuCompiler(gosuCompiler);
    }
}
