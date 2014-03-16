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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.util.Clock;

import java.io.File;
import java.util.List;

public class IncrementalCompilationSupport {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilationSupport.class);
    private final JarSnapshotFeeder jarSnapshotFeeder;
    private final ClassDependencyInfoSerializer dependencyInfoSerializer;
    private FileOperations fileOperations;
    private ClassDependencyInfoExtractor extractor;

    public IncrementalCompilationSupport(JarSnapshotFeeder jarSnapshotFeeder,
                                         ClassDependencyInfoSerializer dependencyInfoSerializer, FileOperations fileOperations, ClassDependencyInfoExtractor extractor) {
        this.jarSnapshotFeeder = jarSnapshotFeeder;
        this.dependencyInfoSerializer = dependencyInfoSerializer;
        this.fileOperations = fileOperations;
        this.extractor = extractor;
    }

    public void compilationComplete(CompileOptions options,
                                    Iterable<JarArchive> jarsOnClasspath, File compiledClassesDir) {
        if (options.isIncremental()) {
            Clock clock = new Clock();
            ClassDependencyInfo info = extractor.extractInfo(compiledClassesDir, "");
            dependencyInfoSerializer.writeInfo(info);
            LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), dependencyInfoSerializer);

            clock = new Clock();
            jarSnapshotFeeder.storeJarSnapshots(jarsOnClasspath, info);
            LOG.lifecycle("Wrote jar snapshots in {}.", clock.getTime());
        }
    }

    public SelectiveCompilation createSelectiveCompilation(IncrementalTaskInputs inputs, FileTree source,
                           FileCollection classpath, File destinationDir, SelectiveJavaCompiler compiler, List<File> sourceDirs) {
        return new SelectiveCompilation(inputs, source, classpath, destinationDir, dependencyInfoSerializer, jarSnapshotFeeder, compiler, sourceDirs, fileOperations);
    }
}
