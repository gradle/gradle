/*
 * Copyright 2014 the original author or authors.
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

/**
 * Creates {@code TextResource}s backed by sources such as strings, files, and archive entries.
 *
 * <p>Example usages:
 *
 * <pre class='autoTested'>
 * def sourcedFromString = resources.text.fromString("some text content")
 *
 * def sourcedFromFile = resources.text.fromFile("path/to/file.txt")
 *
 * task someTask {} // assumption: produces a text file and declares it as output
 * def sourcedFromTask = resources.text.fromFile(someTask)
 *
 * def sourcedFromArchiveEntry =
 *   resources.text.fromArchiveEntry("path/to/archive.zip", "path/to/archive/entry.txt")
 *
 * configurations { someConfig } // assumption: contains a single archive
 * def sourcedFromConfiguration =
 *   resources.text.fromArchiveEntry(configurations.someConfig, "path/to/archive/entry.txt")
 * </pre>
 *
 * File based factory methods optionally accept a character encoding. If no encoding is specified,
 * the platform's default encoding is used.
 *
 * @since 2.2
 */
@Incubating
public interface TextResourceFactory {
    /**
     * Creates a text resource backed by the given string.
     *
     * @param string a string
     * @return a text resource backed by the given string
     */
    TextResource fromString(String string);

    /**
     * Creates a text resource backed by the given file.
     *
     * @param file a text file evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @param charset the file's character encoding (e.g. {@code "utf-8"})
     * @return a text resource backed by the given file
     */
    TextResource fromFile(Object file, String charset);

    /**
     * Same as {@code fromFile(file, Charset.defaultCharset())}.
     */
    TextResource fromFile(Object file);

    /**
     * Creates a text resource backed by the archive entry at the given path within the given archive.
     * The archive format is determined based on the archive's file extension. If the archive format
     * is not supported or cannot be determined, any attempt to access the resource will fail with an exception.
     *
     * @param archive an archive file evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @param entryPath the path to an archive entry
     * @param charset the archive entry's character encoding (e.g. {@code "utf-8"})
     *
     * @return a text resource backed by the archive entry at the given path within the given archive
     */
    TextResource fromArchiveEntry(Object archive, String entryPath, String charset);

    /**
     * Same as {@code fromArchiveEntry(archive, path, Charset.defaultCharset().name())}.
     */
    TextResource fromArchiveEntry(Object archive, String path);
}
