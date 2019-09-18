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

import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.File;
import java.util.Collection;

class SourceFileChangeProcessor {
    private final PreviousCompilation previousCompilation;

    public SourceFileChangeProcessor(PreviousCompilation previousCompilation) {
        this.previousCompilation = previousCompilation;
    }

    public void processChange(File inputFile, Collection<String> classNames, RecompilationSpec spec) {
        spec.getClassesToCompile().addAll(classNames);

        for (String className : classNames) {
            DependentsSet actualDependents = previousCompilation.getDependents(className, IntSets.EMPTY_SET);
            if (actualDependents.isDependencyToAll()) {
                spec.setFullRebuildCause(actualDependents.getDescription(), inputFile);
                return;
            }
            spec.getClassesToCompile().addAll(actualDependents.getAllDependentClasses());
            spec.getResourcesToGenerate().addAll(actualDependents.getDependentResources());
        }
    }
}
