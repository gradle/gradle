/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.vfs;

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkInputListener;
import org.gradle.internal.properties.InputBehavior;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TODO: Maybe merge with AccumulateBuildInputsListener
 */
public class AccumulateUnitOfWorkInputsListener implements WorkInputListener {

    private final UnitOfWorkVfsChangesRegistry changesRegistry;

    public AccumulateUnitOfWorkInputsListener(UnitOfWorkVfsChangesRegistry changesRegistry) {
        this.changesRegistry = changesRegistry;
    }

    @Override
    public void onExecute(UnitOfWork.Identity identity, UnitOfWork work, EnumSet<InputBehavior> relevantBehaviors) {
        Set<String> taskInputs = new LinkedHashSet<>();
        work.visitRegularInputs(new UnitOfWork.InputVisitor() {
            @Override
            public void visitInputFileProperty(String propertyName, InputBehavior behavior, UnitOfWork.InputFileValueSupplier value) {
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
        changesRegistry.registerFileInputs(identity, taskInputs);
    }
}
