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

import org.gradle.api.internal.provider.LazyBackedPatternSet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FilteredFileTree extends CompositeFileTree implements FileCollectionInternal.Source {
    private final FileTreeInternal tree;
    private final Supplier<? extends PatternSet> patternSupplier;
    @Nullable
    private final PatternFilterable declaredPatterns;

    public FilteredFileTree(FileTreeInternal tree, TaskDependencyFactory taskDependencyFactory, PatternSetFactory patternSetFactory, Supplier<? extends PatternSet> patternSupplier) {
        this(tree, taskDependencyFactory, patternSetFactory, patternSupplier, null);
    }

    /**
     * @param declaredPatterns the patterns passed to {@code matching()}, when the supplier derives its patterns from them
     */
    public FilteredFileTree(
        FileTreeInternal tree,
        TaskDependencyFactory taskDependencyFactory,
        PatternSetFactory patternSetFactory,
        Supplier<? extends PatternSet> patternSupplier,
        @Nullable PatternFilterable declaredPatterns
    ) {
        super(taskDependencyFactory, patternSetFactory);
        this.tree = tree;
        this.patternSupplier = patternSupplier;
        this.declaredPatterns = declaredPatterns;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("backing tree");
        formatter.startChildren();
        tree.describeContents(formatter);
        formatter.endChildren();
    }

    public FileTreeInternal getTree() {
        return tree;
    }

    /**
     * The patterns passed to {@code matching()}, if known. Unlike {@link #getPatterns()}, reading this does not calculate the patterns.
     */
    @Nullable
    public PatternFilterable getDeclaredPatterns() {
        return declaredPatterns;
    }

    /**
     * The current set of patterns. Both the instance and the patterns it contains can change over time.
     */
    public PatternSet getPatterns() {
        return patternSupplier.get();
    }

    @Override
    public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
        if (original == this) {
            return supplier.get();
        }
        FileCollectionInternal newTree = tree.replace(original, supplier);
        if (newTree == tree) {
            return this;
        }
        FileTreeInternal newFileTree = newTree instanceof FileTreeInternal ? (FileTreeInternal) newTree : newTree.getAsFileTree();
        return new FilteredFileTree(newFileTree, taskDependencyFactory, patternSetFactory, patternSupplier, declaredPatterns);
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        // For backwards compatibility, need to calculate the patterns on each query
        PatternFilterable patterns = getPatterns();
        tree.visitContentsAsFileTrees(child -> visitor.accept(child.matching(patterns)));
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        tree.visitDependencies(context);
        if (declaredPatterns instanceof LazyBackedPatternSet) {
            // Visits only the producers of the bound value, without calculating it
            ((LazyBackedPatternSet) declaredPatterns).visitDependencies(context);
        }
    }
}
