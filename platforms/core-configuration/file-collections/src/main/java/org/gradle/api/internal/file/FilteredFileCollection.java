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

import com.google.common.collect.Iterators;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public class FilteredFileCollection extends AbstractFileCollection {
    private final FileCollectionInternal collection;
    private final Spec<? super File> filterSpec;

    public FilteredFileCollection(AbstractFileCollection collection, Spec<? super File> filterSpec) {
        super(collection.taskDependencyFactory, collection.patternSetFactory);
        this.collection = collection;
        this.filterSpec = filterSpec;
    }

    @Override
    public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
        AbstractFileCollection newCollection = (AbstractFileCollection) collection.replace(original, supplier);
        if (newCollection == collection) {
            return this;
        }
        return newCollection.filter(filterSpec);
    }

    public FileCollectionInternal getCollection() {
        return collection;
    }

    public Spec<? super File> getFilterSpec() {
        return filterSpec;
    }

    @Override
    public String getDisplayName() {
        return collection.getDisplayName();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        collection.visitDependencies(context);
    }

    @Override
    public Set<File> getFiles() {
        return CollectionUtils.filter(collection, new LinkedHashSet<>(), filterSpec);
    }

    @Override
    public boolean contains(File file) {
        return collection.contains(file) && filterSpec.isSatisfiedBy(file);
    }

    @Override
    public Iterator<File> iterator() {
        return Iterators.filter(collection.iterator(), filterSpec::isSatisfiedBy);
    }
}
