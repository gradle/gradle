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
package org.gradle.api.tasks.util;

import java.util.Set;

/**
 * A {@code PatternFilterable} represents some file container which Ant-style include and exclude patterns can be
 * applied to.
 *
 * Patterns may include:
 * <ul>
 *    <li>'*' to match any number of characters
 *    <li>'?' to match any single character
 *    <li>'**' to match any number of directories or files
 * </ul>
 *
 * Either '/' or '\' may be used in a pattern to separate directories.
 * Patterns ending with '/' or '\' will have '**' automatically appended.
 *
 * Examples:
 * <pre>
 * all files ending with 'jsp' (including subdirectories)
 *    **&#47;*.jsp
 *
 * all files beginning with 'template_' in the level1/level2 directory
 *    level1/level2/template_*
 *
 * all files (including subdirectories) beneath src/main/webapp
 *   src/main/webapp/
 *
 * all files beneath any .svn directory (including subdirectories) under src/main/java
 *   src/main/java/**&#47;.svn/**
 * </pre>
 */
public interface PatternFilterable {

    /**
     * Get the set of include patterns.
     */
    Set<String> getIncludes();

    /**
     * Get the set of exclude patterns.
     */
    Set<String> getExcludes();

    /**
     * Set the allowable include patterns.  Note that unlike {@link #include(Iterable)}
     * this replaces any previously defined includes.
     * @param includes an Iterable providing new include patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable setIncludes(Iterable<String> includes);

    /**
     * Set the allowable exclude patterns.  Note that unlike {@link #exclude(Iterable)}
     * this replaces any previously defined excludes.
     * @param excludes an Iterable providing new exclude patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable setExcludes(Iterable<String> excludes);

    /**
     * Adds an ANT style include pattern.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     *
     * If includes are not provided, then all files beneath the base directory will be included.
     * If includes are provided, then a file must match at least one of the include
     * patterns to be processed.
     * @param includes a vararg list of include patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable include(String... includes);

    /**
     * Adds an ANT style include pattern.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     *
     * If includes are not provided, then all files beneath the base directory will be included.
     * If includes are provided, then a file must match at least one of the include
     * patterns to be processed.
     * @param includes a Iterable providing more include patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable include(Iterable<String> includes);

    /**
     * Adds an ANT style exclude pattern.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     *
     * If excludes are not provided, then no files will be excluded.
     * If excludes are provided, then files must not match any exclude pattern to be processed.
     * @param excludes a vararg list of exclude patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable exclude(String... excludes);

    /**
     * Adds an ANT style exclude pattern.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     *
     * If excludes are not provided, then no files will be excluded.
     * If excludes are provided, then files must not match any exclude pattern to be processed.
     * @param excludes a Iterable providing new exclude patterns
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    PatternFilterable exclude(Iterable<String> excludes);
}
