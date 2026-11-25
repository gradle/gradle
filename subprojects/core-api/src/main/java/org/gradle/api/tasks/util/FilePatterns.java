/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.util;

import org.gradle.api.Incubating;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.internal.DefaultFilePatterns;

import java.util.Set;

/**
 * An immutable set of include and exclude patterns for files, with the same meaning as a {@link PatternSet}.
 *
 * <p>Each method that changes the patterns returns a new value and leaves this one unchanged.
 * A value without includes matches every file that no exclude rejects.</p>
 *
 * @since 10.0.0
 */
@Incubating
public interface FilePatterns {

    /**
     * Returns the value without patterns, which matches every file.
     *
     * @since 10.0.0
     */
    static FilePatterns empty() {
        return DefaultFilePatterns.EMPTY;
    }

    /**
     * The include patterns, in the order they were added.
     *
     * @since 10.0.0
     */
    Set<String> getIncludes();

    /**
     * The exclude patterns, in the order they were added.
     *
     * @since 10.0.0
     */
    Set<String> getExcludes();

    /**
     * The include specs, in the order they were added.
     *
     * @since 10.0.0
     */
    Set<Spec<FileTreeElement>> getIncludeSpecs();

    /**
     * The exclude specs, in the order they were added.
     *
     * @since 10.0.0
     */
    Set<Spec<FileTreeElement>> getExcludeSpecs();

    /**
     * Whether the patterns match case sensitively. The default is {@code true}.
     *
     * @since 10.0.0
     */
    boolean isCaseSensitive();

    /**
     * Whether there are no patterns and no specs.
     *
     * @since 10.0.0
     */
    boolean isEmpty();

    /**
     * Returns a value with the given include patterns added.
     *
     * @since 10.0.0
     */
    FilePatterns include(String... includes);

    /**
     * Returns a value with the given include patterns added.
     *
     * @since 10.0.0
     */
    FilePatterns include(Iterable<String> includes);

    /**
     * Returns a value with the given include spec added.
     *
     * @since 10.0.0
     */
    FilePatterns include(Spec<FileTreeElement> includeSpec);

    /**
     * Returns a value with the given exclude patterns added.
     *
     * @since 10.0.0
     */
    FilePatterns exclude(String... excludes);

    /**
     * Returns a value with the given exclude patterns added.
     *
     * @since 10.0.0
     */
    FilePatterns exclude(Iterable<String> excludes);

    /**
     * Returns a value with the given exclude spec added.
     *
     * @since 10.0.0
     */
    FilePatterns exclude(Spec<FileTreeElement> excludeSpec);

    /**
     * Returns a value whose include patterns are replaced by the given ones. The include specs stay.
     *
     * @since 10.0.0
     */
    FilePatterns withIncludes(Iterable<String> includes);

    /**
     * Returns a value whose exclude patterns are replaced by the given ones. The exclude specs stay.
     *
     * @since 10.0.0
     */
    FilePatterns withExcludes(Iterable<String> excludes);

    /**
     * Returns a value whose include specs are replaced by the given ones. The include patterns stay.
     *
     * @since 10.0.0
     */
    FilePatterns withIncludeSpecs(Iterable<? extends Spec<FileTreeElement>> includeSpecs);

    /**
     * Returns a value whose exclude specs are replaced by the given ones. The exclude patterns stay.
     *
     * @since 10.0.0
     */
    FilePatterns withExcludeSpecs(Iterable<? extends Spec<FileTreeElement>> excludeSpecs);

    /**
     * Returns a value with the given case sensitivity.
     *
     * @since 10.0.0
     */
    FilePatterns caseSensitive(boolean caseSensitive);
}
