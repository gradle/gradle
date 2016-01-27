/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.Buildable;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.io.Serializable;
import java.util.Set;

/**
 * Adapts a {@link MinimalFileSet} into a full {@link org.gradle.api.file.FileCollection}.
 */
public class FileCollectionAdapter extends AbstractFileCollection implements FileCollectionContainer, Serializable {
    private final MinimalFileSet fileCollection;

    public FileCollectionAdapter(MinimalFileSet fileSet) {
        this.fileCollection = fileSet;
    }

    @Override
    public String getDisplayName() {
        return fileCollection.getDisplayName();
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(fileCollection);
    }

    public Set<File> getFiles() {
        return fileCollection.getFiles();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        if (fileCollection instanceof Buildable) {
            Buildable buildable = (Buildable) fileCollection;
            return buildable.getBuildDependencies();
        }
        return super.getBuildDependencies();
    }
}
