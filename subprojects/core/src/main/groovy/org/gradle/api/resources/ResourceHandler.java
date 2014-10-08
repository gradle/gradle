/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.resources;

import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;

import java.io.File;

/**
 * Provides access to resource-specific utility methods, for example factory methods that create various resources.
 */
public interface ResourceHandler {

    /**
     * Creates resource that points to a gzip compressed file at the given path.
     * The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    ReadableResource gzip(Object path);

    /**
     * Creates resource that points to a bzip2 compressed file at the given path.
     * The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    ReadableResource bzip2(Object path);

    /**
     * Creates a text resource backed by the given string.
     *
     * @param string a string
     * @return a text resource backed by the given string
     *
     * @since 2.2
     */
    @Incubating
    TextResource text(String string);

    /**
     * Creates a text resource backed by the given file.
     *
     * @param file a text file
     * @param charset the file's character encoding (e.g. {@code "utf-8"})
     * @return a text resource backed by the given file
     *
     * @since 2.2
     */
    @Incubating
    TextResource fileText(File file, String charset);

    /**
     * Same as {@code text(file, Charset.defaultCharset())}.
     *
     * @since 2.2
     */
    @Incubating
    TextResource fileText(File file);

    /**
     * Same as {@code text(fileCollection.singleFile, charset)}, except that the file collection
     * will be evaluated lazily and its {@link org.gradle.api.Buildable} information preserved.
     *
     * @since 2.2
     */
    @Incubating
    TextResource fileText(FileCollection file, String charset);

    /**
     * Same as {@code text(fileCollection, Charset.defaultCharset().name())}.
     *
     * @since 2.2
     */
    @Incubating
    TextResource fileText(FileCollection file);

    /**
     * Creates a text resource backed by the archive entry at the given path within the given archive.
     * The archive format is determined based on the archive's file extension. If the archive format
     * is not supported or cannot be determined, any attempt to access the resource will fail with an exception.
     *
     * @param archive an archive file
     * @param entryPath the path to an archive entry
     * @param charset the archive entry's character encoding (e.g. {@code "utf-8"})
     *
     * @return a text resource backed by the archive entry at the given path within the given archive
     *
     * @since 2.2
     */
    @Incubating
    TextResource archiveEntryText(File archive, String entryPath, String charset);

    /**
     * Same as {@code archiveEntryText(archive, path, Charset.defaultCharset().name())}.
     *
     * @since 2.2
     */
    @Incubating
    TextResource archiveEntryText(File archive, String path);

    /**
     * Same as {@code archiveEntryText(fileCollection.singleFile, path, charset)}, except that the file
     * collection will be evaluated lazily and its {@link org.gradle.api.Buildable} information preserved.
     *
     * @since 2.2
     */
    @Incubating
    TextResource archiveEntryText(FileCollection archive, String entryPath, String charset);

    /**
     * Same as {@code archiveEntryText(fileCollection, path, Charset.defaultCharset().name())}.
     *
     * @since 2.2
     */
    @Incubating
    TextResource archiveEntryText(FileCollection archive, String entryPath);
}
