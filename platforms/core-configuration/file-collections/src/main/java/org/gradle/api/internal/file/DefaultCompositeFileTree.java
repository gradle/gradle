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

package org.gradle.api.internal.file;

import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.util.internal.PatternSetFactory;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class DefaultCompositeFileTree extends CompositeFileTree {
    private final Collection<? extends FileTreeInternal> fileTrees;

    public DefaultCompositeFileTree(TaskDependencyFactory taskDependencyFactory, PatternSetFactory patternSetFactory, List<? extends FileTreeInternal> fileTrees) {
        super(taskDependencyFactory, patternSetFactory);
        this.fileTrees = fileTrees;
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        for (FileTreeInternal fileTree : fileTrees) {
            visitor.accept(fileTree);
        }
    }

    @Override
    public String getDisplayName() {
        return "file tree";
    }
}
