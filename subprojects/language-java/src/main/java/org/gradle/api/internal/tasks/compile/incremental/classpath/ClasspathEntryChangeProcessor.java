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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.incremental.InputFileDetails;

public class ClasspathEntryChangeProcessor {

    private final FileOperations fileOperations;
    private final ClasspathChangeDependentsFinder dependentsFinder;

    public ClasspathEntryChangeProcessor(FileOperations fileOperations, ClasspathSnapshot classpathSnapshot, PreviousCompilation previousCompilation) {
        this.fileOperations = fileOperations;
        this.dependentsFinder = new ClasspathChangeDependentsFinder(classpathSnapshot, previousCompilation);
    }

    public void processChange(InputFileDetails input, RecompilationSpec spec) {
        ClasspathEntry classpathEntry = new ClasspathEntry(input.getFile(), fileOperations.zipTree(input.getFile()));
        DependentsSet actualDependents = dependentsFinder.getActualDependents(input, classpathEntry);
        if (actualDependents.isDependencyToAll()) {
            spec.setFullRebuildCause(actualDependents.getDescription(), input.getFile());
            return;
        }
        spec.getClassesToCompile().addAll(actualDependents.getDependentClasses());
    }
}
