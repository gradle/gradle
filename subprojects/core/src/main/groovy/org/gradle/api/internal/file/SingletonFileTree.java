/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.file.*;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Collection;

class SingletonFileTree extends CompositeFileTree {
    private final File file;
    private final TaskDependency builtBy;

    public SingletonFileTree(File file, TaskDependency builtBy) {
        this.file = file;
        this.builtBy = builtBy;
    }

    @Override
    public String getDisplayName() {
        return String.format("file '%s'", file);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return builtBy;
    }

    protected void addSourceCollections(Collection<FileCollection> sources) {
        if (file.isDirectory()) {
            sources.add(new DefaultConfigurableFileTree(file, null, null));
        } else if (file.isFile()) {
            sources.add(new FileFileTree());
        }
    }

    private class FileVisitDetailsImpl extends DefaultFileTreeElement implements FileVisitDetails {
        private FileVisitDetailsImpl() {
            super(file, new RelativePath(true, file.getName()));
        }

        public void stopVisiting() {
        }
    }

    private class FileFileTree extends AbstractFileTree {
        public String getDisplayName() {
            return SingletonFileTree.this.getDisplayName();
        }

        public FileTree visit(FileVisitor visitor) {
            visitor.visitFile(new FileVisitDetailsImpl());
            return this;
        }
    }
}
