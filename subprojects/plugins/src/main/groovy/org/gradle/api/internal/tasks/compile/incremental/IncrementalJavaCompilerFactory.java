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
import org.gradle.api.internal.hash.Hasher;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompilationCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassDependencyInfoCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.*;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.List;

public class IncrementalJavaCompilerFactory {

    private final IncrementalCompilationSupport incrementalSupport;

    public IncrementalJavaCompilerFactory(Project project, String compileTaskPath, CleaningJavaCompiler cleaningJavaCompiler,
                                          List<Object> source, CompilationCaches compilationCaches, JavaCompile javaCompile) {
        //bunch of services that enable incremental java compilation.
        Hasher hasher = new DefaultHasher(); //TODO SF use caching hasher
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(), hasher, compilationCaches.getClassAnalysisCache());
        JarSnapshotter jarSnapshotter = new CachingJarSnapshotter(new DefaultJarSnapshotter(hasher, analyzer), hasher, compilationCaches.getJarSnapshotCache());

        String cacheFileBaseName = compileTaskPath.replaceAll(":", "_"); //TODO SF weak. Instead of this, local caches should use standard caching mechanism with scope of task
        LocalJarSnapshots localJarSnapshots = new LocalJarSnapshots(compilationCaches.getCacheRepository(), javaCompile, compilationCaches.getJarSnapshotCache(), hasher);
        LocalClassDependencyInfoCache localClassDependencyInfo = new LocalClassDependencyInfoCache(new File(project.getBuildDir(), cacheFileBaseName + "-class-info.bin"));

        JarSnapshotsMaker jarSnapshotsMaker = new JarSnapshotsMaker(localJarSnapshots, jarSnapshotter, new ClasspathJarFinder((FileOperations) project));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs(source);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs); //TODO SF replace with converter that parses input source class
        RecompilationSpecProvider recompilationSpecProvider = new RecompilationSpecProvider(sourceToNameConverter, localClassDependencyInfo, (FileOperations) project, jarSnapshotter, localJarSnapshots);
        ClassDependencyInfoUpdater classDependencyInfoUpdater = new ClassDependencyInfoUpdater(localClassDependencyInfo, (FileOperations) project, analyzer);
        incrementalSupport = new IncrementalCompilationSupport(jarSnapshotsMaker, localClassDependencyInfo, (FileOperations) project,
                cleaningJavaCompiler, compileTaskPath, recompilationSpecProvider, classDependencyInfoUpdater, sourceDirs);
    }

    public Compiler<JavaCompileSpec> createCompiler(IncrementalTaskInputs inputs) {
        return incrementalSupport.prepareCompiler(inputs);
    }
}
