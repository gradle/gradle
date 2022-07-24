/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link org.gradle.api.file.FileCollection} that contains the union of zero or more file collections. Maintains file ordering.
 *
 * <p>The source file collections are calculated from the result of calling {@link #visitChildren(Consumer)}, and may be lazily created.
 * </p>
 *
 * <p>The dependencies of this collection are calculated from the result of calling {@link #visitDependencies(TaskDependencyResolveContext)}.</p>
 */
public abstract class CompositeFileCollection extends AbstractFileCollection implements TaskDependencyContainer {
    public CompositeFileCollection(Factory<PatternSet> patternSetFactory) {
        super(patternSetFactory);
    }

    public CompositeFileCollection() {
    }

    @Override
    public boolean contains(File file) {
        for (FileCollection collection : getSourceCollections()) {
            if (collection.contains(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        for (FileCollection collection : getSourceCollections()) {
            if (!collection.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void addAsResourceCollection(Object builder, String nodeName) {
        for (FileCollection fileCollection : getSourceCollections()) {
            fileCollection.addToAntBuilder(builder, nodeName, AntType.ResourceCollection);
        }
    }

    @Override
    public FileCollectionInternal filter(final Spec<? super File> filterSpec) {
        return new CompositeFileCollection(patternSetFactory) {
            @Override
            public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
                FileCollectionInternal newCollection = CompositeFileCollection.this.replace(original, supplier);
                if (newCollection == CompositeFileCollection.this) {
                    return this;
                }
                return newCollection.filter(filterSpec);
            }

            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                CompositeFileCollection.this.visitChildren(child -> visitor.accept(child.filter(filterSpec)));
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }
        };
    }

    abstract protected void visitChildren(Consumer<FileCollectionInternal> visitor);

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        visitChildren(context::add);
    }

    protected List<? extends FileCollectionInternal> getSourceCollections() {
        ImmutableList.Builder<FileCollectionInternal> builder = ImmutableList.builder();
        visitChildren(builder::add);
        return builder.build();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        visitChildren(child -> child.visitStructure(visitor));
    }
}
