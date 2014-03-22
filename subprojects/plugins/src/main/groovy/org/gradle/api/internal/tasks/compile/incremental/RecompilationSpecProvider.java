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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoProvider;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarArchive;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarChangeDependentsFinder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotFeeder;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.util.Collection;
import java.util.LinkedHashSet;

public class RecompilationSpecProvider {

    private final SourceToNameConverter sourceToNameConverter;
    private final ClassDependencyInfoProvider dependencyInfoProvider;
    private final FileOperations fileOperations;
    private final JarSnapshotFeeder jarSnapshotFeeder;

    public RecompilationSpecProvider(SourceToNameConverter sourceToNameConverter, ClassDependencyInfoProvider dependencyInfoProvider, FileOperations fileOperations, JarSnapshotFeeder jarSnapshotFeeder) {
        this.sourceToNameConverter = sourceToNameConverter;
        this.dependencyInfoProvider = dependencyInfoProvider;
        this.fileOperations = fileOperations;
        this.jarSnapshotFeeder = jarSnapshotFeeder;
    }

    public RecompilationSpec provideRecompilationInfo(IncrementalTaskInputs inputs) {
        final ClassDependencyInfo dependencyInfo = dependencyInfoProvider.provideInfo();
        InputChangeAction action = new InputChangeAction(dependencyInfo);
        inputs.outOfDate(action);
        if (action.fullRebuildReason != null) {
            return action;
        }
        inputs.removed(action);
        return action;
    }

    private class InputChangeAction implements Action<InputFileDetails>, RecompilationSpec {
        private final Collection<String> classesToCompile = new LinkedHashSet<String>();
        private final ClassDependencyInfo dependencyInfo;
        private String fullRebuildReason;

        public InputChangeAction(ClassDependencyInfo dependencyInfo) {
            this.dependencyInfo = dependencyInfo;
        }

        public void execute(InputFileDetails input) {
            if (fullRebuildReason != null) {
                return;
            }
            String name = input.getFile().getName();
            if (name.endsWith(".java")) {
                String className = sourceToNameConverter.getClassName(input.getFile());
                classesToCompile.add(className);
                DependentsSet actualDependents = dependencyInfo.getRelevantDependents(className);
                if (actualDependents.isDependencyToAll()) {
                    fullRebuildReason = "change to " + className + " requires full rebuild";
                    return;
                }
                classesToCompile.addAll(actualDependents.getDependentClasses());
            }
            if (name.endsWith(".jar")) {
                JarArchive jarArchive = new JarArchive(input.getFile(), fileOperations.zipTree(input.getFile()));
                JarChangeDependentsFinder dependentsFinder = new JarChangeDependentsFinder(jarSnapshotFeeder);
                DependentsSet actualDependents = dependentsFinder.getActualDependents(input, jarArchive);
                if (actualDependents.isDependencyToAll()) {
                    fullRebuildReason = "change to " + input.getFile() + " requires full rebuild";
                    return;
                }
                classesToCompile.addAll(actualDependents.getDependentClasses());
            }
        }

        public Collection<String> getClassNames() {
            return classesToCompile;
        }

        public boolean isFullRebuildNeeded() {
            return fullRebuildReason != null;
        }

        public String getFullRebuildReason() {
            return fullRebuildReason;
        }
    }
}
