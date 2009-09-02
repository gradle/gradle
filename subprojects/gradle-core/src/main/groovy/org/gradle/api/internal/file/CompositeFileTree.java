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

import groovy.lang.Closure;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.List;

public abstract class CompositeFileTree extends CompositeFileCollection implements FileTree {
    @Override
    protected abstract Iterable<? extends FileTree> getSourceCollections();

    public FileTree plus(FileTree fileTree) {
        return new UnionFileTree(this, fileTree);
    }

    public FileTree matching(Closure filterConfigClosure) {
        return new FilteredFileTree(filterConfigClosure);
    }

    public FileTree matching(PatternSet patterns) {
        return new FilteredFileTree(patterns);
    }

    private class FilteredFileTree extends CompositeFileTree {
        private final Closure closure;
        private final PatternSet patterns;

        public FilteredFileTree(Closure closure) {
            this.closure = closure;
            patterns = null;
        }

        public FilteredFileTree(PatternSet patterns) {
            this.patterns = patterns;
            closure = null;
        }

        @Override
        public String getDisplayName() {
            return CompositeFileTree.this.getDisplayName();
        }

        @Override
        protected Iterable<? extends FileTree> getSourceCollections() {
            List<FileTree> filteredSets = new ArrayList<FileTree>();
            for (FileTree set : CompositeFileTree.this.getSourceCollections()) {
                if (closure != null) {
                    filteredSets.add(set.matching(closure));
                } else {
                    filteredSets.add(set.matching(patterns));
                }
            }
            return filteredSets;
        }
    }
}
