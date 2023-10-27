/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.tooling.internal.provider.continuous;

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.WorkInputListener;
import org.gradle.internal.properties.InputBehavior;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class AccumulateBuildInputsListener implements WorkInputListener {

    private final BuildInputHierarchy buildInputHierarchy;

    public AccumulateBuildInputsListener(BuildInputHierarchy buildInputHierarchy) {
        this.buildInputHierarchy = buildInputHierarchy;
    }

    @Override
    public void onExecute(UnitOfWork.Identity identity, UnitOfWork work, EnumSet<InputBehavior> relevantBehaviors) {
        Set<String> taskInputs = new LinkedHashSet<>();
        Set<FilteredTree> filteredFileTreeTaskInputs = new LinkedHashSet<>();
        work.visitRegularInputs(new InputVisitor() {
            @Override
            public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
                if (relevantBehaviors.contains(behavior)) {
                    ((FileCollectionInternal) value.getFiles()).visitStructure(new FileCollectionStructureVisitor() {
                        @Override
                        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                            contents.forEach(location -> taskInputs.add(location.getAbsolutePath()));
                        }

                        @Override
                        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                            if (patterns.isEmpty()) {
                                taskInputs.add(root.getAbsolutePath());
                            } else {
                                filteredFileTreeTaskInputs.add(new FilteredTree(root.getAbsolutePath(), patterns));
                            }
                        }

                        @Override
                        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                            taskInputs.add(file.getAbsolutePath());
                        }
                    });
                }
            }
        });
        buildInputHierarchy.recordInputs(identity, taskInputs);
        filteredFileTreeTaskInputs.forEach(fileTree -> buildInputHierarchy.recordFilteredInput(fileTree.getRoot(), fileTree.getPatterns().getAsSpec()));
    }

    private static class FilteredTree {
        private final String root;
        private final PatternSet patterns;

        private FilteredTree(String root, PatternSet patterns) {
            this.root = root;
            this.patterns = patterns;
        }

        public String getRoot() {
            return root;
        }

        public PatternSet getPatterns() {
            return patterns;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FilteredTree that = (FilteredTree) o;
            return root.equals(that.root) && patterns.equals(that.patterns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, patterns);
        }
    }
}
