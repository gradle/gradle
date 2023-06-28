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

import groovy.lang.Closure;
import org.gradle.api.file.ReadOnlyFileTreeElement;
import org.gradle.api.specs.Spec;

import java.util.Set;

/**
 * <p>A {@code PatternFilterable} represents some file container which Ant-style include and exclude patterns or specs
 * can be applied to.</p>
 *
 * <p>Patterns may include:</p>
 *
 * <ul>
 *
 * <li>'*' to match any number of characters
 *
 * <li>'?' to match any single character
 *
 * <li>'**' to match any number of directories or files
 *
 * </ul>
 *
 * <p>Either '/' or '\' may be used in a pattern to separate directories. Patterns ending with '/' or '\' will have '**'
 * automatically appended.</p>
 *
 * <p>Examples:</p>
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
 *
 * <p>You may also use a closure or {@link Spec} to specify which files to include or exclude. The closure or {@link Spec}
 * is passed a {@link org.gradle.api.file.ReadOnlyFileTreeElement}, and must return a boolean value.</p>
 *
 * <p>If no include patterns or specs are specified, then all files in this container will be included. If any include
 * patterns or specs are specified, then a file is included if it matches any of the patterns or specs.</p>
 *
 * <p>If no exclude patterns or spec are specified, then no files will be excluded. If any exclude patterns or specs are
 * specified, then a file is include only if it matches none of the patterns or specs.</p>
 */
public interface PatternFilterable {

    /**
     * Returns the set of include patterns.
     *
     * @return The include patterns. Returns an empty set when there are no include patterns.
     */
    Set<String> getIncludes();

    /**
     * Returns the set of exclude patterns.
     *
     * @return The exclude patterns. Returns an empty set when there are no exclude patterns.
     */
    Set<String> getExcludes();

    /**
     * Set the allowable include patterns.  Note that unlike {@link #include(Iterable)} this replaces any previously
     * defined includes.
     *
     * @param includes an Iterable providing new include patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable setIncludes(Iterable<String> includes);

    /**
     * Set the allowable exclude patterns.  Note that unlike {@link #exclude(Iterable)} this replaces any previously
     * defined excludes.
     *
     * @param excludes an Iterable providing new exclude patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable setExcludes(Iterable<String> excludes);

    /**
     * Adds an ANT style include pattern. This method may be called multiple times to append new patterns and multiple
     * patterns may be specified in a single call.
     *
     * If includes are not provided, then all files in this container will be included. If includes are provided, then a
     * file must match at least one of the include patterns to be processed.
     *
     * @param includes a vararg list of include patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable include(String... includes);

    /**
     * Adds an ANT style include pattern. This method may be called multiple times to append new patterns and multiple
     * patterns may be specified in a single call.
     *
     * If includes are not provided, then all files in this container will be included. If includes are provided, then a
     * file must match at least one of the include patterns to be processed.
     *
     * @param includes a Iterable providing more include patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable include(Iterable<String> includes);

    /**
     * Adds an include spec. This method may be called multiple times to append new specs.
     *
     * If includes are not provided, then all files in this container will be included. If includes are provided, then a
     * file must match at least one of the include patterns or specs to be included.
     *
     * @param includeSpec the spec to add
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable include(Spec<ReadOnlyFileTreeElement> includeSpec);

    /**
     * Adds an include spec. This method may be called multiple times to append new specs. The given closure is passed a
     * {@link org.gradle.api.file.ReadOnlyFileTreeElement} as its parameter.
     *
     * If includes are not provided, then all files in this container will be included. If includes are provided, then a
     * file must match at least one of the include patterns or specs to be included.
     *
     * @param includeSpec the spec to add
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable include(Closure includeSpec);

    /**
     * Adds an ANT style exclude pattern. This method may be called multiple times to append new patterns and multiple
     * patterns may be specified in a single call.
     *
     * If excludes are not provided, then no files will be excluded. If excludes are provided, then files must not match
     * any exclude pattern to be processed.
     *
     * @param excludes a vararg list of exclude patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable exclude(String... excludes);

    /**
     * Adds an ANT style exclude pattern. This method may be called multiple times to append new patterns and multiple
     * patterns may be specified in a single call.
     *
     * If excludes are not provided, then no files will be excluded. If excludes are provided, then files must not match
     * any exclude pattern to be processed.
     *
     * @param excludes a Iterable providing new exclude patterns
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable exclude(Iterable<String> excludes);

    /**
     * Adds an exclude spec. This method may be called multiple times to append new specs.
     *
     * If excludes are not provided, then no files will be excluded. If excludes are provided, then files must not match
     * any exclude pattern to be processed.
     *
     * @param excludeSpec the spec to add
     * @return this
     * @see PatternFilterable Pattern Format
     */
    PatternFilterable exclude(Spec<ReadOnlyFileTreeElement> excludeSpec);

    /**
     * Adds an exclude spec. This method may be called multiple times to append new specs.The given closure is passed a
     * {@link org.gradle.api.file.ReadOnlyFileTreeElement} as its parameter. The closure should return true or false. Example:
     *
     * <pre class='autoTested'>
     * copySpec {
     *   from 'source'
     *   into 'destination'
     *   //an example of excluding files from certain configuration:
     *   exclude { it.file in configurations.someConf.files }
     * }
     * </pre>
     *
     * If excludes are not provided, then no files will be excluded. If excludes are provided, then files must not match
     * any exclude pattern to be processed.
     *
     * @param excludeSpec the spec to add
     * @return this
     * @see ReadOnlyFileTreeElement
     */
    PatternFilterable exclude(Closure excludeSpec);
}
