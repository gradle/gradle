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
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoProvider;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class StaleClassesDetecter {

    private final SourceToNameConverter sourceToNameConverter;
    private final ClassDependencyInfoProvider dependencyInfoProvider;
    private final FileOperations fileOperations;
    private final JarSnapshotFeeder jarSnapshotFeeder;

    public StaleClassesDetecter(SourceToNameConverter sourceToNameConverter, ClassDependencyInfoProvider dependencyInfoProvider, FileOperations fileOperations, JarSnapshotFeeder jarSnapshotFeeder) {
        this.sourceToNameConverter = sourceToNameConverter;
        this.dependencyInfoProvider = dependencyInfoProvider;
        this.fileOperations = fileOperations;
        this.jarSnapshotFeeder = jarSnapshotFeeder;
    }

    public StaleClasses detectStaleClasses(IncrementalTaskInputs inputs) {
        final ClassDependencyInfo dependencyInfo = dependencyInfoProvider.readInfo();
        InputChangeAction action = new InputChangeAction(dependencyInfo);
        inputs.outOfDate(action);
        if (action.fullRebuildReason != null) {
            return action;
        }
        inputs.removed(action);
        return action;
    }

    private class InputChangeAction implements Action<InputFileDetails>, StaleClasses {
        private final List<String> staleClasses = new LinkedList<String>();
        private final ClassDependencyInfo dependencyInfo;
        private String fullRebuildReason;

        public InputChangeAction(ClassDependencyInfo dependencyInfo) {
            this.dependencyInfo = dependencyInfo;
        }

        public void execute(InputFileDetails inputFileDetails) {
            if (fullRebuildReason != null) {
                return;
            }
            File inputFile = inputFileDetails.getFile();
            String name = inputFile.getName();
            if (name.endsWith(".java")) {
                String className = sourceToNameConverter.getClassName(inputFile);
                staleClasses.add(className);
                Set<String> actualDependents = dependencyInfo.getActualDependents(className);
                if (actualDependents == null) {
                    fullRebuildReason = "change to " + className + " requires full rebuild";
                    return;
                }
                staleClasses.addAll(actualDependents);
            }
            if (name.endsWith(".jar")) {
                JarArchive jarArchive = new JarArchive(inputFileDetails.getFile(), fileOperations.zipTree(inputFileDetails.getFile()));
                JarChangeProcessor processor = new JarChangeProcessor(jarSnapshotFeeder, dependencyInfo);
                RebuildInfo rebuildInfo = processor.processJarChange(inputFileDetails, jarArchive);
                RebuildInfo.Info info = rebuildInfo.configureCompilation(staleClasses);
                if (info == RebuildInfo.Info.FullRebuild) {
                    fullRebuildReason = "change to " + inputFile + " requires full rebuild";
                }
            }
        }

        public Collection<String> getClassNames() {
            return staleClasses;
        }

        public boolean isFullRebuildNeeded() {
            return fullRebuildReason != null;
        }

        public String getFullRebuildReason() {
            return fullRebuildReason;
        }
    }
}
