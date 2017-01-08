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
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;

public class ClassFilesAnalyzer implements FileVisitor {
    private final ClassDependenciesAnalyzer analyzer;
    private final ClassDependentsAccumulator accumulator;

    public ClassFilesAnalyzer(ClassDependenciesAnalyzer analyzer) {
        this(analyzer, new ClassDependentsAccumulator());
    }

   ClassFilesAnalyzer(ClassDependenciesAnalyzer analyzer, ClassDependentsAccumulator accumulator) {
        this.analyzer = analyzer;
        this.accumulator = accumulator;
    }

    @Override
    public void visitDir(FileVisitDetails dirDetails) {}

    @Override
    public void visitFile(FileVisitDetails fileDetails) {
        if (!fileDetails.getName().endsWith(".class")) {
            return;
        }
        String className = fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", "");

        ClassAnalysis analysis = analyzer.getClassAnalysis(className, fileDetails);
        accumulator.addClass(className, analysis.isDependencyToAll(), analysis.getClassDependencies(), analysis.getConstants(), analysis.getLiterals());
    }

    public ClassSetAnalysisData getAnalysis() {
        return new ClassSetAnalysisData(accumulator.getDependentsMap(), accumulator.getClassesToConstants(), accumulator.getLiteralsToClasses());
    }
}
