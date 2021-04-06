/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.work.FileChange;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

abstract class AbstractRecompilationSpecProvider implements RecompilationSpecProvider {
    private final Deleter deleter;
    protected final FileOperations fileOperations;
    protected final FileTree sourceTree;

    public AbstractRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sourceTree
    ) {
        this.deleter = deleter;
        this.fileOperations = fileOperations;
        this.sourceTree = sourceTree;
    }

    protected void processClasspathChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        DependentsSet dependents = current.findDependentsOfClasspathChanges(previous);
        if (dependents.isDependencyToAll()) {
            spec.setFullRebuildCause(dependents.getDescription());
            return;
        }
        spec.addClassesToCompile(dependents.getPrivateDependentClasses());
        spec.addClassesToCompile(dependents.getAccessibleDependentClasses());
        spec.addResourcesToGenerate(dependents.getDependentResources());
    }

    protected boolean deleteStaleFilesIn(PatternSet filesToDelete, final File destinationDir) {
        if (filesToDelete == null || filesToDelete.isEmpty() || destinationDir == null) {
            return false;
        }
        Set<File> toDelete = fileOperations.fileTree(destinationDir).matching(filesToDelete).getFiles();
        return StaleOutputCleaner.cleanOutputs(deleter, toDelete, destinationDir);
    }

    protected void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = new HashSet<>(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClasses(classesToProcess);
    }

    protected void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = new ArrayList<>(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    protected String rebuildClauseForChangedNonSourceFile(String description, FileChange fileChange) {
        return String.format("%s '%s' has been %s", description, fileChange.getFile().getName(), fileChange.getChangeType().name().toLowerCase(Locale.US));
    }
}
