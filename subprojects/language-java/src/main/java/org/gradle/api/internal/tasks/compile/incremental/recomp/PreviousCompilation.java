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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PreviousCompilation {
    private final PreviousCompilationData data;
    private final ClassSetAnalysis classAnalysis;

    public PreviousCompilation(PreviousCompilationData data) {
        this.data = data;
        this.classAnalysis = new ClassSetAnalysis(data.getOutputSnapshot(), data.getAnnotationProcessingData(), data.getCompilerApiData());
    }

    @Nullable
    public ClassSetAnalysis getClasspath() {
        return new ClassSetAnalysis(data.getClasspathSnapshot());
    }

    public DependentsSet findDependentsOfClasspathChanges(ClassSetAnalysis.ClassSetDiff diff) {
        if (diff.getDependents().isDependencyToAll()) {
            return diff.getDependents();
        }
        return classAnalysis.findTransitiveDependents(diff.getDependents().getAllDependentClasses(), diff.getConstants());
    }

    public DependentsSet findDependentsOfSourceChanges(Set<String> classNames) {
        return classAnalysis.findTransitiveDependents(classNames, classNames.stream().collect(Collectors.toMap(Function.identity(), classAnalysis::getConstants)));
    }

    public Set<String> getTypesToReprocess(Set<String> compiledClasses) {
        return classAnalysis.getTypesToReprocess(compiledClasses);
    }

    public SourceFileClassNameConverter getSourceToClassConverter() {
        return new DefaultSourceFileClassNameConverter(data.getCompilerApiData().getSourceToClassMapping());
    }
}
