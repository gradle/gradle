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
import org.gradle.api.Action;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.Cast;

import java.util.Collection;

import static org.gradle.api.internal.file.AbstractFileTree.fileVisitorFrom;

/**
 * A {@link FileTree} that contains the union of zero or more file trees.
 */
public abstract class CompositeFileTree extends CompositeFileCollection implements FileTreeInternal {
    protected Collection<? extends FileTreeInternal> getSourceCollections() {
        return Cast.uncheckedCast(super.getSourceCollections());
    }

    public FileTree plus(FileTree fileTree) {
        return new UnionFileTree(this, Cast.cast(FileTreeInternal.class, fileTree));
    }

    public FileTree matching(final Closure filterConfigClosure) {
        return new FilteredFileTree() {
            @Override
            protected FileTree filter(FileTree set) {
                return set.matching(filterConfigClosure);
            }
        };
    }

    @Override
    public FileTree matching(final Action<? super PatternFilterable> filterConfigAction) {
        return new FilteredFileTree() {
            @Override
            protected FileTree filter(FileTree set) {
                return set.matching(filterConfigAction);
            }
        };
    }

    public FileTree matching(final PatternFilterable patterns) {
        return new FilteredFileTree() {
            @Override
            protected FileTree filter(FileTree set) {
                return set.matching(patterns);
            }
        };
    }

    public FileTree visit(Closure visitor) {
        return visit(fileVisitorFrom(visitor));
    }

    @Override
    public FileTree visit(Action<? super FileVisitDetails> visitor) {
        for (FileTree tree : getSourceCollections()) {
            tree.visit(visitor);
        }
        return this;
    }

    public FileTree visit(FileVisitor visitor) {
        for (FileTree tree : getSourceCollections()) {
            tree.visit(visitor);
        }
        return this;
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }

    @Override
    public FileTree getAsFileTree() {
        return this;
    }

    private abstract class FilteredFileTree extends CompositeFileTree {

        protected abstract FileTree filter(FileTree set);

        @Override
        public String getDisplayName() {
            return CompositeFileTree.this.getDisplayName();
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            ResolvableFileCollectionResolveContext nestedContext = context.newContext();
            CompositeFileTree.this.visitContents(nestedContext);
            for (FileTree set : nestedContext.resolveAsFileTrees()) {
                context.add(filter(set));
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            CompositeFileTree.this.visitDependencies(context);
        }
    }
}
