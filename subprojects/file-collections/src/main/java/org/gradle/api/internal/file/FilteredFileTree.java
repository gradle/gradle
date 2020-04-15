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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.util.function.Supplier;

public class FilteredFileTree extends CompositeFileTree implements FileCollectionInternal.Source {
    private final CompositeFileTree tree;
    private final Supplier<? extends PatternSet> patternSupplier;

    public FilteredFileTree(CompositeFileTree tree, Supplier<? extends PatternSet> patternSupplier) {
        super(tree.patternSetFactory);
        this.tree = tree;
        this.patternSupplier = patternSupplier;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    public CompositeFileTree getTree() {
        return tree;
    }

    /**
     * The current set of patterns. Both the instance and the patterns it contains can change over time.
     */
    public PatternSet getPatterns() {
        return patternSupplier.get();
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        // For backwards compatibility, need to calculate the patterns on each query
        PatternFilterable patterns = getPatterns();
        ResolvableFileCollectionResolveContext nestedContext = context.newContext();
        tree.visitContents(nestedContext);
        for (FileTree set : nestedContext.resolveAsFileTrees()) {
            context.add(set.matching(patterns));
        }
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        tree.visitDependencies(context);
    }
}
