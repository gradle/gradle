/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.file;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.io.FilterReader;
import java.util.regex.Pattern;

/**
 * A set of specifications for copying files.  This includes:
 *
 * <ul>
 *
 * <li>source directories (multiples allowed)
 *
 * <li>destination directory
 *
 * <li>ANT like include patterns
 *
 * <li>ANT like exclude patterns
 *
 * <li>File relocating rules
 *
 * <li>renaming rules
 *
 * <li>content filters
 *
 * </ul>
 *
 * CopySpecs may be nested by passing a closure to one of the from methods.  The closure creates a child CopySpec and
 * delegates methods in the closure to the child. Child CopySpecs inherit any values specified in the parent. This
 * allows constructs like:
 * <pre>
 * into('webroot')
 * exclude('**&#47;.svn/**')
 * from('src/main/webapp') {
 *    include '**&#47;*.jsp'
 * }
 * from('src/main/js') {
 *    include '**&#47;*.js'
 * }
 * </pre>
 *
 * In this example, the <code>into</code> and <code>exclude</code> specifications at the root level are inherited by the
 * two child CopySpecs.
 *
 * @author Steve Appling
 * @see org.gradle.api.tasks.Copy Copy Task
 * @see org.gradle.api.Project#copy(groovy.lang.Closure) Project.copy()
 */
public interface CopySpec extends CopySourceSpec, CopyProcessingSpec, PatternFilterable {
    /**
     * Specifies whether case-sensitive pattern matching should be used.
     *
     * @return true for case-sensitive matching.
     */
    boolean isCaseSensitive();

    /**
     * Specifies whether case-sensitive pattern matching should be used for this CopySpec.
     *
     * @param caseSensitive true for case-sensitive matching.
     */
    void setCaseSensitive(boolean caseSensitive);

    /**
     * Tells if empty target directories will be included in the copy.
     *
     * @return <tt>true</tt> if empty target directories will be included in the copy, <tt>false</tt> otherwise
     */
    boolean getIncludeEmptyDirs();

    /**
     * Controls if empty target directories should be included in the copy.
     *
     * @param includeEmptyDirs <tt>true</tt> if empty target directories should be included in the copy, <tt>false</tt> otherwise
     */
    void setIncludeEmptyDirs(boolean includeEmptyDirs);

    /**
     * The strategy for handling more than one file with the same path name. The exclude strategy will
     * skip subsequent files with a path that has already been encountered. This is set to inherit by default,
     * which will use the behavior of the parent spec. If no parent spec is explicitly set, then inherit will
     * act as include. Also accepts strings in the form 'include', 'exclude', or 'inherit'.
     *
     * @return the strategy for handling duplicate file paths
     */
    DuplicatesStrategy getDuplicatesStrategy();

    /**
     * Sets the default strategy for handling files with duplicate path names. This strategy can be overridden for
     * individual files by configuring FileCopyDetails.
     * @param strategy Can be DuplicatesStrategy.inherit (default), include, or exclude
     */
    void setDuplicatesStrategy(DuplicatesStrategy strategy);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path matches the specified Ant-style pattern.
     * This is equivalent to using eachFile() and selectively applying a configuration based on the file's path.
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param closure Configuration applied to the FileCopyDetails of each file matching pattern
     * @return this
     */
    CopySpec matching(String pattern, Closure closure);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path matches the specified Ant-style pattern.
     * This is equivalent to using eachFile() and selectively applying a configuration based on the file's path.
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file matching pattern
     * @return this
     */
    CopySpec matching(String pattern, Action<? super FileCopyDetails> action);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path does not match the specified
     * Ant-style pattern. This is equivalent to using eachFile() and selectively applying a configuration based on the
     * file's path.
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param closure Configuration applied to the FileCopyDetails of each file that does not match pattern
     * @return this
     */
    CopySpec notMatching(String pattern, Closure closure);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path does not match the specified
     * Ant-style pattern. This is equivalent to using eachFile() and selectively applying a configuration based on the
     * file's path.
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file that does not match pattern
     * @return this
     */
    CopySpec notMatching(String pattern, Action<? super FileCopyDetails> action);

    /**
     * Adds the given specs as a child of this spec.
     * @param sourceSpecs The specs to add
     * @return this
     */
    CopySpec with(CopySpec... sourceSpecs);

    // CopySourceSpec overrides to broaden return type

    /**
     * {@inheritDoc}
     */
    CopySpec from(Object... sourcePaths);

    /**
     * {@inheritDoc}
     */
    CopySpec from(Object sourcePath, Closure c);

    // PatternFilterable overrides to broaden return type

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec setIncludes(Iterable<String> includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec setExcludes(Iterable<String> excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(String... includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(Iterable<String> includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(Spec<FileTreeElement> includeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(Closure includeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(String... excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(Iterable<String> excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(Spec<FileTreeElement> excludeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(Closure excludeSpec);

    // CopyProcessingSpec overrides to broaden return type

    /**
     * {@inheritDoc}
     */
    CopySpec into(Object destPath);

    /**
     * Creates and configures a child {@code CopySpec} with the given destination path.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param destPath Path to the destination directory for a Copy
     * @param configureClosure The closure to use to configure the child {@code CopySpec}.
     * @return this
     */
    CopySpec into(Object destPath, Closure configureClosure);

    /**
     * {@inheritDoc}
     */
    CopySpec rename(Closure closure);

    /**
     * {@inheritDoc}
     */
    CopySpec rename(String sourceRegEx, String replaceWith);

    /**
     * {@inheritDoc}
     */
    CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Class<? extends FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Closure closure);

    /**
     * {@inheritDoc}
     */
    CopySpec expand(Map<String, ?> properties);

    /**
     * {@inheritDoc}
     */
    CopySpec eachFile(Action<? super FileCopyDetails> action);

    /**
     * {@inheritDoc}
     */
    CopySpec eachFile(Closure closure);
}
