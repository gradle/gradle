/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class SubtractingFileCollection extends AbstractOpaqueFileCollection {
    private final AbstractFileCollection left;
    private final FileCollection right;

    public SubtractingFileCollection(AbstractFileCollection left, FileCollection right) {
        super(left.taskDependencyFactory, left.patternSetFactory);
        this.left = left;
        this.right = right;
    }

    public AbstractFileCollection getLeft() {
        return left;
    }

    public FileCollection getRight() {
        return right;
    }

    @Override
    public String getDisplayName() {
        return "file collection";
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        left.visitDependencies(context);
    }

    @Override
    protected Set<File> getIntrinsicFiles() {
        Set<File> files = new LinkedHashSet<File>(left.getFiles());
        files.removeAll(right.getFiles());
        return files;
    }

    @Override
    public boolean contains(File file) {
        return left.contains(file) && !right.contains(file);
    }
}
