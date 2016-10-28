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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.cache.Stash;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;

public class ClassSetAnalysisUpdater {

    private final static Logger LOG = Logging.getLogger(ClassSetAnalysisUpdater.class);

    private final Stash<ClassSetAnalysisData> stash;
    private final FileOperations fileOperations;
    private ClassDependenciesAnalyzer analyzer;

    public ClassSetAnalysisUpdater(Stash<ClassSetAnalysisData> stash, FileOperations fileOperations, ClassDependenciesAnalyzer analyzer) {
        this.stash = stash;
        this.fileOperations = fileOperations;
        this.analyzer = analyzer;
    }

    public void updateAnalysis(JavaCompileSpec spec) {
        Timer clock = Timers.startTimer();
        FileTree tree = fileOperations.fileTree(spec.getDestinationDir());
        ClassFilesAnalyzer analyzer = new ClassFilesAnalyzer(this.analyzer);
        tree.visit(analyzer);
        ClassSetAnalysisData data = analyzer.getAnalysis();
        stash.put(data);
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
    }
}
