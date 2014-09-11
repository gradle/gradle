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

import java.io.File;

public class ClassFilesAnalyzer implements FileVisitor {

    private final ClassDependenciesAnalyzer analyzer;
    private final String packagePrefix;
    private final ClassDependentsAccumulator accumulator;

    public ClassFilesAnalyzer(ClassDependenciesAnalyzer analyzer) {
        this(analyzer, "", new ClassDependentsAccumulator(""));
    }

    ClassFilesAnalyzer(ClassDependenciesAnalyzer analyzer, String packagePrefix, ClassDependentsAccumulator accumulator) {
        this.analyzer = analyzer;
        this.packagePrefix = packagePrefix;
        this.accumulator = accumulator;
    }

    public void visitDir(FileVisitDetails dirDetails) {}

    public void visitFile(FileVisitDetails fileDetails) {
        File file = fileDetails.getFile();
        if (!file.getName().endsWith(".class")) {
            return;
        }
        String className = fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", "");
        if (!className.startsWith(packagePrefix)) {
            return;
        }

        ClassAnalysis analysis = analyzer.getClassAnalysis(className, file);
        accumulator.addClass(className, analysis.isDependencyToAll(), analysis.getClassDependencies());
    }

    public ClassSetAnalysisData getAnalysis() {
        return new ClassSetAnalysisData(accumulator.getDependentsMap());
    }
}