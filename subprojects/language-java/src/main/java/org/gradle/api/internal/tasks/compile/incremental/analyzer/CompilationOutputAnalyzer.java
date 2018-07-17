/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import com.google.common.collect.Sets;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.File;
import java.util.Set;

public class CompilationOutputAnalyzer {

    private final static Logger LOG = Logging.getLogger(CompilationOutputAnalyzer.class);

    private final FileOperations fileOperations;
    private ClassDependenciesAnalyzer analyzer;
    private final FileHasher fileHasher;

    public CompilationOutputAnalyzer(FileOperations fileOperations, ClassDependenciesAnalyzer analyzer, FileHasher fileHasher) {
        this.fileOperations = fileOperations;
        this.analyzer = analyzer;
        this.fileHasher = fileHasher;
    }

    public ClassSetAnalysisData getAnalysis(JavaCompileSpec spec, WorkResult result) {
        Timer clock = Time.startTimer();
        CompilationResultAnalyzer analyzer = new CompilationResultAnalyzer(this.analyzer, fileHasher);
        visitClassFiles(spec, analyzer);
        ClassSetAnalysisData data = analyzer.getAnalysis();
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
        return data;
    }

    private void visitClassFiles(JavaCompileSpec spec, CompilationResultAnalyzer analyzer) {
        Set<File> baseDirs = Sets.newLinkedHashSet();
        baseDirs.add(spec.getDestinationDir());
        for (File baseDir : baseDirs) {
            fileOperations.fileTree(baseDir).visit(analyzer);
        }
    }
}
