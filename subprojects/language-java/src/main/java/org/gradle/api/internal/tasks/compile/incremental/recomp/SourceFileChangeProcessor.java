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
}
