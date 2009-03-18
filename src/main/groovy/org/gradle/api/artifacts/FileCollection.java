/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.artifacts;

import java.io.File;
import java.util.Set;

/**
 * <p>A {@code FileCollection} represents a collection of files which you can query in certain ways.</p>
 */
public interface FileCollection extends Iterable<File> {
    /**
     * Returns the content of this collection, asserting it contains exactly one file.
     *
     * @return The file.
     * @throws IllegalStateException when this collection does not contain exactly one file.
     */
    File getSingleFile() throws IllegalStateException;

    /**
     * Returns the contents of this collection.
     *
     * @return The files. Returns an empty set if this collection is empty.
     */
    Set<File> getFiles();

    /**
     * Returns the contents of this collection as a path.
     *
     * @return The path. Returns an empty string if this collection is empty.
     */
    String getAsPath();
}
