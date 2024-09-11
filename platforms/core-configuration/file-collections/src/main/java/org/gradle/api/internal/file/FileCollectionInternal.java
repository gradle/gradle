/*
 * Copyright 2015 the original author or authors.
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


import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.logging.text.TreeFormatter;

import java.io.File;
import java.util.Optional;
import java.util.function.Supplier;

public interface FileCollectionInternal extends FileCollection, TaskDependencyContainer {
    String DEFAULT_COLLECTION_DISPLAY_NAME = "file collection";

    @Override
    FileCollectionInternal filter(Spec<? super File> filterSpec);

    @Override
    FileTreeInternal getAsFileTree();

    /**
     * Returns a copy of this collection, with the given collection replaced with the value returned by the given supplier.
     *
     * This is used to deal with the case where a mutable collection may be added to itself. This is intended to become an error at some point.
     */
    FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier);

    /**
     * Visits the structure of this collection, that is, zero or more atomic sources of files.
     *
     * <p>The implementation should call the most specific methods on {@link FileCollectionStructureVisitor} that it is able to.</p>
     */
    void visitStructure(FileCollectionStructureVisitor visitor);

    /**
     * Returns the display name of this file collection. Used in log and error messages.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Appends diagnostic information about the contents of this collection to the given formatter.
     */
    TreeFormatter describeContents(TreeFormatter formatter);

    /**
     * Calculates the execution time value of this file collection. The resulting value is serialized to the configuration cache
     * and deserialized at execution time, utilizing the logic encapsulated within the value.
     */
    default Optional<FileCollectionExecutionTimeValue> calculateExecutionTimeValue() {
        return Optional.empty();
    }

    /**
     * Some representation of a source of files.
     */
    interface Source {
    }

    /**
     * An opaque source of files.
     */
    Source OTHER = new Source() {
    };
}
