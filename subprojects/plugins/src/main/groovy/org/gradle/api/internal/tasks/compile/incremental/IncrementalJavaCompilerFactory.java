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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassDependencyInfoCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.ClasspathJarFinder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotsMaker;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.LocalJarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.util.List;

public class IncrementalJavaCompilerFactory {

    private final IncrementalCompilationSupport incrementalSupport;

    public IncrementalJavaCompilerFactory(Project project, String compileTaskPath, CleaningJavaCompiler cleaningJavaCompiler, List<Object> source) {
        //bunch of services that enable incremental java compilation.
        ClassDependenciesAnalyzer analyzer = new ClassDependenciesAnalyzer(); //TODO SF needs cross-project caching
        JarSnapshotter jarSnapshotter = new JarSnapshotter(new DefaultHasher(), analyzer); //TODO SF needs cross-project caching

        String cacheFileBaseName = compileTaskPath.replaceAll(":", "_"); //TODO SF weak. task can be renamed in place of a task that was deleted.
        LocalJarSnapshotCache jarSnapshotCache = new LocalJarSnapshotCache(new File(project.getBuildDir(), cacheFileBaseName + "-jar-snapshot-cache.bin"));
        LocalClassDependencyInfoCache localClassDependencyInfo = new LocalClassDependencyInfoCache(new File(project.getBuildDir(), cacheFileBaseName + "-class-info.bin"));

        JarSnapshotsMaker jarSnapshotsMaker = new JarSnapshotsMaker(jarSnapshotCache, jarSnapshotter, new ClasspathJarFinder((FileOperations) project));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs(source);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs); //TODO SF replace with converter that parses input source class
        RecompilationSpecProvider recompilationSpecProvider = new RecompilationSpecProvider(sourceToNameConverter, localClassDependencyInfo, (FileOperations) project, jarSnapshotter, jarSnapshotCache);
        ClassDependencyInfoUpdater classDependencyInfoUpdater = new ClassDependencyInfoUpdater(localClassDependencyInfo, (FileOperations) project, analyzer);
        incrementalSupport = new IncrementalCompilationSupport(jarSnapshotsMaker, localClassDependencyInfo, (FileOperations) project,
                cleaningJavaCompiler, compileTaskPath, recompilationSpecProvider, classDependencyInfoUpdater, sourceDirs);
    }

    public Compiler<JavaCompileSpec> createCompiler(IncrementalTaskInputs inputs) {
        return incrementalSupport.prepareCompiler(inputs);
    }
}
