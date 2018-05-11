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

package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;

public class CompilationResultAnalyzer implements FileVisitor {
    private static final Logger LOGGER = Logging.getLogger(CompilationResultAnalyzer.class);

    private final ClassDependenciesAnalyzer analyzer;
    private final ClassDependentsAccumulator accumulator;
    private final FileHasher hasher;

    public CompilationResultAnalyzer(ClassDependenciesAnalyzer analyzer, FileHasher fileHasher) {
        this(analyzer, fileHasher, new ClassDependentsAccumulator());
    }

    CompilationResultAnalyzer(ClassDependenciesAnalyzer analyzer, FileHasher fileHasher, ClassDependentsAccumulator accumulator) {
        this.analyzer = analyzer;
        this.hasher = fileHasher;
        this.accumulator = accumulator;
    }

    @Override
    public void visitDir(FileVisitDetails dirDetails) {
    }

    @Override
    public void visitFile(FileVisitDetails fileDetails) {
        if (!fileDetails.getName().endsWith(".class")) {
            return;
        }

        HashCode hash = hasher.hash(fileDetails);
        try {
            ClassAnalysis analysis = analyzer.getClassAnalysis(hash, fileDetails);
            accumulator.addClass(fileDetails.getFile(), analysis);
        } catch (Exception e) {
            accumulator.fullRebuildNeeded("class file " + fileDetails.getName() + " could not be analyzed. See the debug log for more details");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not analyze class file " + fileDetails.getName(), e);
            }
        }

    }

    public ClassSetAnalysisData getAnalysis() {
        return accumulator.getAnalysis();
    }

    public void visitAnnotationProcessingResult(AnnotationProcessingResult annotationProcessingResult) {
        if (annotationProcessingResult == null) {
            accumulator.fullRebuildNeeded("the chosen compiler did not support incremental annotation processing");
        } else {
            accumulator.addAnnotationProcessingResult(annotationProcessingResult);
        }
    }
}
