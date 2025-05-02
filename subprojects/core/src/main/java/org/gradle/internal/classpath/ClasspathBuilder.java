/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import java.io.File;
import java.io.IOException;

/**
 * Helper for building a jar.
 */
public interface ClasspathBuilder {

    /**
     * Creates a Jar file using the given action to add entries to the file. If the file already exists it will be replaced.
     */
    void jar(File destinationFile, Action action);

    /**
     * Creates a directory using the given action to add entries to it. If the directory already exists, it will be cleared first.
     *
     * @param destinationDir the directory to place entries into
     * @param action the action to populate the directory
     */
    void directory(File destinationDir, Action action);

    @FunctionalInterface
    interface Action {
        void execute(EntryBuilder builder) throws IOException;
    }

    interface EntryBuilder {
        default void put(String name, byte[] content) throws IOException {
            put(name, content, ClasspathEntryVisitor.Entry.CompressionMethod.UNDEFINED);
        }
        void put(String name, byte[] content, ClasspathEntryVisitor.Entry.CompressionMethod compressionMethod) throws IOException;
    }
}
