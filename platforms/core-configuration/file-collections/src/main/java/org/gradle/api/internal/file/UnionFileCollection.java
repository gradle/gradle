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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An immutable sequence of file collections.
 */
public class UnionFileCollection extends CompositeFileCollection {
    private final ImmutableSet<FileCollectionInternal> source;

    public UnionFileCollection(TaskDependencyFactory taskDependencyFactory, FileCollectionInternal... source) {
        super(taskDependencyFactory);
        this.source = ImmutableSet.copyOf(source);
    }

    public UnionFileCollection(TaskDependencyFactory taskDependencyFactory, Iterable<? extends FileCollectionInternal> source) {
        super(taskDependencyFactory);
        this.source = ImmutableSet.copyOf(source);
    }

    @Override
    public String getDisplayName() {
        return "file collection";
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("source");
        formatter.startChildren();
        for (FileCollectionInternal files : source) {
            files.describeContents(formatter);
        }
        formatter.endChildren();
    }

    public Set<? extends FileCollection> getSources() {
        return source;
    }

    @Override
    public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
        ImmutableSet.Builder<FileCollectionInternal> newSource = ImmutableSet.builderWithExpectedSize(source.size());
        boolean hasChanges = false;
        for (FileCollectionInternal candidate : source) {
            FileCollectionInternal newCollection = candidate.replace(original, supplier);
            hasChanges |= newCollection != candidate;
            newSource.add(newCollection);
        }
        if (hasChanges) {
            return new UnionFileCollection(taskDependencyFactory, newSource.build());
        } else {
            return this;
        }
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        for (FileCollectionInternal fileCollection : source) {
            visitor.accept(fileCollection);
        }
    }

    @Override
    public Optional<FileCollectionExecutionTimeValue> calculateExecutionTimeValue() {
        ImmutableSet.Builder<FileCollectionExecutionTimeValue> builder = new ImmutableSet.Builder<>();
        for (FileCollectionInternal fileCollection : source) {
            Optional<FileCollectionExecutionTimeValue> executionTimeValue = fileCollection.calculateExecutionTimeValue();
            if (!executionTimeValue.isPresent()) {
                return Optional.empty();
            }
            builder.add(executionTimeValue.get());
        }

        return Optional.of(new UnionExecutionTimeValue(builder.build()));
    }

    private static class UnionExecutionTimeValue implements FileCollectionExecutionTimeValue {
        private final ImmutableSet<FileCollectionExecutionTimeValue> source;

        public UnionExecutionTimeValue(ImmutableSet<FileCollectionExecutionTimeValue> source) {
            this.source = source;
        }

        @Override
        public FileCollectionInternal toFileCollection(FileCollectionFactory fileCollectionFactory) {
            return source.stream()
                .map(value -> value.toFileCollection(fileCollectionFactory))
                .reduce(FileCollectionFactory.empty(), (acc, collection) -> (FileCollectionInternal) acc.plus(collection));
        }
    }
}
