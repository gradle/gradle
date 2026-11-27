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

import java.util.LinkedHashSet;
import java.util.Set;

class SourceFileChangeProcessor {
    private final PreviousCompilation previousCompilation;

    public SourceFileChangeProcessor(PreviousCompilation previousCompilation) {
        this.previousCompilation = previousCompilation;
    }

    public void processChange(Set<String> classNames, RecompilationSpec spec) {
        spec.addClassesToCompile(classNames);
        DependentsSet actualDependents = previousCompilation.findDependentsOfSourceChanges(classNames);
        if (actualDependents.isDependencyToAll()) {
            spec.setFullRebuildCause(actualDependents.getDescription());
            return;
        }
        spec.addClassesToCompile(actualDependents.getAllDependentClasses());
        spec.addResourcesToGenerate(actualDependents.getDependentResources());
    }

    /**
     * Adds the dependents of classes that are recompiled although their source did not change. Unlike for a changed class,
     * their constants are not taken as changed: without constants mapping, a changed constant means a full recompilation.
     */
    public void processDependentsOfRecompiledClasses(Set<String> classNames, RecompilationSpec spec) {
        DependentsSet dependents = previousCompilation.findDependentsOfRecompiledClasses(classNames);
        if (dependents.isDependencyToAll()) {
            spec.setFullRebuildCause(dependents.getDescription());
            return;
        }
        spec.addClassesToCompile(dependents.getAllDependentClasses());
        spec.addResourcesToGenerate(dependents.getDependentResources());
    }

    public Set<String> processAnnotationDependenciesOfIndependentClasses(Set<String> classNames, RecompilationSpec spec) {
        Set<String> newAdded = new LinkedHashSet<>();
        for (String className : classNames) {
            DependentsSet annotationProcessingDependentsSet = previousCompilation.getAnnotationProcessingDependentsSet(className);
            for (String classToCompile : annotationProcessingDependentsSet.getAllDependentClasses()) {
                if (spec.addClassToCompile(classToCompile)) {
                    newAdded.add(classToCompile);
                }
            }
            spec.addResourcesToGenerate(annotationProcessingDependentsSet.getDependentResources());
        }
        return newAdded;
    }
}
