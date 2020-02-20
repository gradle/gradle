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

import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

public class FileCollectionBackFileTree extends CompositeFileTree {
    private final AbstractFileCollection collection;

    public FileCollectionBackFileTree(Factory<PatternSet> patternSetFactory, AbstractFileCollection collection) {
        super(patternSetFactory);
        this.collection = collection;
    }

    public AbstractFileCollection getCollection() {
        return collection;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        ResolvableFileCollectionResolveContext nested = context.newContext();
        nested.add(collection);
        context.addAll(nested.resolveAsFileTrees());
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(collection);
    }

    @Override
    public String getDisplayName() {
        return collection.getDisplayName();
    }
}
