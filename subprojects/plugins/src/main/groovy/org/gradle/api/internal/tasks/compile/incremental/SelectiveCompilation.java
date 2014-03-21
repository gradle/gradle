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

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.Clock;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class SelectiveCompilation implements org.gradle.api.internal.tasks.compile.Compiler<JavaCompileSpec> {
    private static final Logger LOG = Logging.getLogger(SelectiveCompilation.class);
    private final IncrementalTaskInputs inputs;
    private final ClassDependencyInfoSerializer dependencyInfoSerializer;
    private final JarSnapshotFeeder jarSnapshotFeeder;
    private final CleaningJavaCompiler cleaningCompiler;
    private final CompilationSourceDirs sourceDirs;
    private final FileOperations fileOperations;
    private String fullRebuildNeeded;

    public SelectiveCompilation(IncrementalTaskInputs inputs, ClassDependencyInfoSerializer dependencyInfoSerializer, JarSnapshotFeeder jarSnapshotFeeder,
                                CleaningJavaCompiler cleaningCompiler, CompilationSourceDirs sourceDirs, FileOperations fileOperations) {
        this.inputs = inputs;
        this.dependencyInfoSerializer = dependencyInfoSerializer;
        this.jarSnapshotFeeder = jarSnapshotFeeder;
        this.cleaningCompiler = cleaningCompiler;
        this.sourceDirs = sourceDirs;
        this.fileOperations = fileOperations;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        Clock clock = new Clock();
        final SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs.getSourceDirs());

        //load dependency info
        final ClassDependencyInfo dependencyInfo = dependencyInfoSerializer.readInfo();

        //stale classes for deletion
        final List<String> staleClasses = new LinkedList<String>();

        inputs.outOfDate(new Action<InputFileDetails>() {
            public void execute(InputFileDetails inputFileDetails) {
                if (fullRebuildNeeded != null) {
                    return;
                }
                File inputFile = inputFileDetails.getFile();
                String name = inputFile.getName();
                if (name.endsWith(".java")) {
                    String className = sourceToNameConverter.getClassName(inputFile);
                    staleClasses.add(className);
                    Set<String> actualDependents = dependencyInfo.getActualDependents(className);
                    if (actualDependents == null) {
                        fullRebuildNeeded = "change to " + className + " requires full rebuild";
                        return;
                    }
                    staleClasses.addAll(actualDependents);
                }
                if (name.endsWith(".jar")) {
                    JarArchive jarArchive = new JarArchive(inputFileDetails.getFile(), fileOperations.zipTree(inputFileDetails.getFile()));
                    JarChangeProcessor processor = new JarChangeProcessor(jarSnapshotFeeder, dependencyInfo);
                    RebuildInfo rebuildInfo = processor.processJarChange(inputFileDetails, jarArchive);
                    RebuildInfo.Info info = rebuildInfo.configureCompilation(staleClasses);
                    if (info == RebuildInfo.Info.FullRebuild) {
                        fullRebuildNeeded = "change to " + inputFile + " requires full rebuild";
                        return;
                    }
                }
            }
        });
        if (fullRebuildNeeded != null) {
            LOG.lifecycle("Stale classes detection completed in {}. Rebuild needed: {}.", clock.getTime(), fullRebuildNeeded);
            return cleaningCompiler.execute(spec);
        }
        inputs.removed(new Action<InputFileDetails>() {
            public void execute(InputFileDetails inputFileDetails) {
                //TODO SF not really implemented yet
                if (inputFileDetails.getFile().getName().endsWith(".java")) {
                    staleClasses.add(sourceToNameConverter.getClassName(inputFileDetails.getFile()));
                }
            }
        });

        if (staleClasses.isEmpty()) {
            //hurray! Compilation not needed!
            return new WorkResult() {
                public boolean getDidWork() {
                    return true;
                }
            };
        }

        PatternSet classesToDelete = new PatternSet();
        PatternSet sourceToCompile = new PatternSet();

        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            classesToDelete.include(path.concat(".class")); //TODO SF remember about inner classes
            sourceToCompile.include(path.concat(".java"));
        }

        //selectively configure the source
        spec.setSource(spec.getSource().getAsFileTree().matching(sourceToCompile));
        //since we're compiling selectively we need to include the classes compiled previously
        spec.setClasspath(Iterables.concat(spec.getClasspath(), asList(spec.getDestinationDir())));
        //get rid of stale files
        Set<File> staleClassFiles = fileOperations.fileTree(spec.getDestinationDir()).matching(classesToDelete).getFiles();
        for (File staleClassFile : staleClassFiles) {
            staleClassFile.delete();
        }

        try {
            //use the original compiler to avoid cleaning up all the files
            return cleaningCompiler.getCompiler().execute(spec);
        } finally {
            LOG.lifecycle("Stale classes detection completed in {}. Stale classes: {}, Compile include patterns: {}, Files to delete: {}",
                    clock.getTime(), classesToDelete.getIncludes().size(), sourceToCompile.getIncludes(), staleClassFiles.size());
        }
    }
}
