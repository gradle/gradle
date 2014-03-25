/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.Clock;

import java.io.File;
import java.util.Set;

public class SelectiveCompilation {
    private final FileCollection source;
    private final FileCollection classpath;
    private final JarSnapshotFeeder jarSnapshotFeeder;
    private SelectiveJavaCompiler compiler;
    private static final Logger LOG = Logging.getLogger(SelectiveCompilation.class);
    private String fullRebuildNeeded;
    private boolean compilationNeeded = true;
    private final FileOperations operations;

    public SelectiveCompilation(IncrementalTaskInputs inputs, FileTree source, FileCollection compileClasspath, final File compileDestination,
                                final ClassDependencyInfoSerializer dependencyInfoSerializer, final JarSnapshotCache jarSnapshotCache, final SelectiveJavaCompiler compiler,
                                Iterable<File> sourceDirs, final FileOperations operations) {
        this.operations = operations;
        this.jarSnapshotFeeder = new JarSnapshotFeeder(jarSnapshotCache, new JarSnapshotter(new DefaultHasher()));
        this.compiler = compiler;

        Clock clock = new Clock();
        final InputOutputMapper mapper = new InputOutputMapper(sourceDirs, compileDestination);

        //load dependency info
        final ClassDependencyInfo dependencyInfo = dependencyInfoSerializer.readInfo();

        //including only source java classes that were changed
        final PatternSet changedSourceOnly = new PatternSet();
        InputFileDetailsAction action = new InputFileDetailsAction(mapper, compiler, changedSourceOnly, dependencyInfo);
        inputs.outOfDate(action);
        inputs.removed(action);
        if (fullRebuildNeeded != null) {
            LOG.lifecycle("Stale classes detection completed in {}. Rebuild needed: {}.", clock.getTime(), fullRebuildNeeded);
            this.classpath = compileClasspath;
            this.source = source;
            return;
        }

        compiler.deleteStaleClasses();
        Set<File> filesToCompile = source.matching(changedSourceOnly).getFiles();
        if (filesToCompile.isEmpty()) {
            this.compilationNeeded = false;
            this.classpath = compileClasspath;
            this.source = source;
        } else {
            this.classpath = compileClasspath.plus(new SimpleFileCollection(compileDestination));
            this.source = source.matching(changedSourceOnly);
        }
        LOG.lifecycle("Stale classes detection completed in {}. Compile include patterns: {}.", clock.getTime(), changedSourceOnly.getIncludes());
    }

    public FileCollection getSource() {
        return source;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public boolean getCompilationNeeded() {
        return compilationNeeded;
    }

    public boolean getFullRebuildRequired() {
        return fullRebuildNeeded != null;
    }

    private class InputFileDetailsAction implements Action<InputFileDetails> {
        private final InputOutputMapper mapper;
        private final SelectiveJavaCompiler compiler;
        private final PatternSet changedSourceOnly;
        private final ClassDependencyInfo dependencyInfo;

        public InputFileDetailsAction(InputOutputMapper mapper, SelectiveJavaCompiler compiler, PatternSet changedSourceOnly, ClassDependencyInfo dependencyInfo) {
            this.mapper = mapper;
            this.compiler = compiler;
            this.changedSourceOnly = changedSourceOnly;
            this.dependencyInfo = dependencyInfo;
        }

        public void execute(InputFileDetails inputFileDetails) {
            if (fullRebuildNeeded != null) {
                return;
            }
            File inputFile = inputFileDetails.getFile();
            String name = inputFile.getName();
            if (name.endsWith(".java")) {
                JavaSourceClass source = mapper.toJavaSourceClass(inputFile);
                compiler.addStaleClass(source);
                changedSourceOnly.include(source.getRelativePath());
                Set<String> actualDependents = dependencyInfo.getActualDependents(source.getClassName());
                if (actualDependents == null) {
                    fullRebuildNeeded = "change to " + source.getClassName() + " requires full rebuild";
                    return;
                }
                for (String d : actualDependents) {
                    JavaSourceClass dSource = mapper.toJavaSourceClass(d);
                    compiler.addStaleClass(dSource);
                    changedSourceOnly.include(dSource.getRelativePath());
                }
            }
            if (name.endsWith(".jar")) {
                fullRebuildNeeded = "change to " + inputFile + " requires full rebuild";
                return;
            }
        }
    }
}
