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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.jar.DefaultJarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarArchive;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarChangeDependentsFinder;
import org.gradle.api.internal.tasks.compile.incremental.model.PreviousCompilation;
import org.gradle.api.tasks.incremental.InputFileDetails;

class JarChangeProcessor {

    private final FileOperations fileOperations;
    private final DefaultJarSnapshotter jarSnapshotter;
    private final PreviousCompilation previousCompilation;

    public JarChangeProcessor(FileOperations fileOperations, DefaultJarSnapshotter jarSnapshotter, PreviousCompilation previousCompilation) {
        this.fileOperations = fileOperations;
        this.jarSnapshotter = jarSnapshotter;
        this.previousCompilation = previousCompilation;
    }

    public void processChange(InputFileDetails input, DefaultRecompilationSpec spec) {
        JarArchive jarArchive = new JarArchive(input.getFile(), fileOperations.zipTree(input.getFile()));
        JarChangeDependentsFinder dependentsFinder = new JarChangeDependentsFinder(jarSnapshotter, previousCompilation);
        DependentsSet actualDependents = dependentsFinder.getActualDependents(input, jarArchive);
        if (actualDependents.isDependencyToAll()) {
            spec.fullRebuildCause = input.getFile();
            return;
        }
        spec.classesToCompile.addAll(actualDependents.getDependentClasses());
    }
}
