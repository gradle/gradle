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

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoWriter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.Clock;

public class IncrementalCompilationFinalizer implements Compiler<JavaCompileSpec> {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilationFinalizer.class);

    private final Compiler<JavaCompileSpec> delegate;
    private final ClassDependencyInfoExtractor extractor;
    private final ClassDependencyInfoWriter dependencyInfoWriter;
    private final JarSnapshotFeeder jarSnapshotFeeder;
    private final ClasspathJarFinder classpathJarFinder;

    public IncrementalCompilationFinalizer(Compiler<JavaCompileSpec> delegate, ClassDependencyInfoExtractor extractor, ClassDependencyInfoWriter dependencyInfoWriter,
                                           JarSnapshotFeeder jarSnapshotFeeder, ClasspathJarFinder classpathJarFinder) {
        this.delegate = delegate;
        this.extractor = extractor;
        this.dependencyInfoWriter = dependencyInfoWriter;
        this.jarSnapshotFeeder = jarSnapshotFeeder;
        this.classpathJarFinder = classpathJarFinder;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult out = delegate.execute(spec);

        Clock clock = new Clock();
        ClassDependencyInfo info = extractor.extractInfo(spec.getDestinationDir(), "");
        dependencyInfoWriter.writeInfo(info);
        LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), dependencyInfoWriter);

        clock = new Clock();
        jarSnapshotFeeder.storeJarSnapshots(classpathJarFinder.findJarArchives(spec.getClasspath()), info);
        LOG.lifecycle("Created and wrote jar snapshots in {}.", clock.getTime());

        return out;
    }
}