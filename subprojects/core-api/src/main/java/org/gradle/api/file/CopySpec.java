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
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.jspecify.annotations.Nullable;

import java.io.FilterReader;
import java.util.Map;
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
 * <pre class='autoTested'>
 * def myCopySpec = project.copySpec {
 *   into('webroot')
 *   exclude('**&#47;.data/**')
 *   from('src/main/webapp') {
 *     include '**&#47;*.jsp'
 *   }
 *   from('src/main/js') {
 *     include '**&#47;*.js'
 *   }
 * }
 * </pre>
 *
 * In this example, the <code>into</code> and <code>exclude</code> specifications at the root level are inherited by the
 * two child CopySpecs.
 *
 * Copy specs can be reused in other copy specs via {@link #with(CopySpec...)} method. This enables reuse of the copy spec instances.
 *
 * <pre class='autoTested'>
 * def contentSpec = copySpec {
 *   from("content") {
 *     include "**&#47;*.txt"
 *   }
 * }
 *
 * task copy(type: Copy) {
 *   into "$buildDir/copy"
 *   with contentSpec
 * }
 * </pre>
 *
 * @see org.gradle.api.tasks.Copy Copy Task
 * @see org.gradle.api.Project#copy(groovy.lang.Closure) Project.copy()
 */
@HasInternalProtocol
public interface CopySpec extends CopySourceSpec, CopyProcessingSpec, PatternFilterable {
    /**
     * Specifies whether case-sensitive pattern matching should be used.
     *
     * @return true for case-sensitive matching.
     */
    @ToBeReplacedByLazyProperty
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
     * @return <code>true</code> if empty target directories will be included in the copy, <code>false</code> otherwise
     */
    @ToBeReplacedByLazyProperty
    boolean getIncludeEmptyDirs();

    /**
     * Controls if empty target directories should be included in the copy.
     *
     * @param includeEmptyDirs <code>true</code> if empty target directories should be included in the copy, <code>false</code> otherwise
     */
    void setIncludeEmptyDirs(boolean includeEmptyDirs);

    /**
     * Returns the strategy to use when trying to copy more than one file to the same destination.
     * <p>
     * The value can be set with a case insensitive string of the enum value (e.g. {@code 'exclude'} for {@link DuplicatesStrategy#EXCLUDE}).
     * <p>
     * This strategy can be overridden for individual files by using {@link #eachFile(org.gradle.api.Action)} or {@link #filesMatching(String, org.gradle.api.Action)}.
     *
     * @return the strategy to use for files included by this copy spec.
     * @see DuplicatesStrategy
     */
    @ToBeReplacedByLazyProperty
    DuplicatesStrategy getDuplicatesStrategy();

    /**
     * The strategy to use when trying to copy more than one file to the same destination.
     * Defaults to {@link DuplicatesStrategy#INHERIT}, the strategy inherited from the parent copy spec.
     * If no explicit deduplication strategy is set, but duplicates are found, an error is thrown.
     */
    void setDuplicatesStrategy(DuplicatesStrategy strategy);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path matches the specified Ant-style pattern.
     * This is equivalent to using eachFile() and selectively applying a configuration based on the file's path.
     *
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file matching pattern
     * @return this
     */
    CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path matches any of the specified Ant-style patterns.
     * This is equivalent to using eachFile() and selectively applying a configuration based on the file's path.
     *
     * @param patterns Ant-style patterns used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file matching pattern
     * @return this
     */
    CopySpec filesMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path does not match the specified
     * Ant-style pattern. This is equivalent to using eachFile() and selectively applying a configuration based on the
     * file's path.
     *
     * @param pattern Ant-style pattern used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file that does not match pattern
     * @return this
     */
    CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action);

    /**
     * Configure the {@link org.gradle.api.file.FileCopyDetails} for each file whose path does not match any of the specified
     * Ant-style patterns. This is equivalent to using eachFile() and selectively applying a configuration based on the
     * file's path.
     *
     * @param patterns Ant-style patterns used to match against files' relative paths
     * @param action action called for the FileCopyDetails of each file that does not match any pattern
     * @return this
     */
    CopySpec filesNotMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action);

    /**
     * Adds the given specs as a child of this spec.
     *
     * <pre class='autoTested'>
     * def contentSpec = copySpec {
     *   from("content") {
     *     include "**&#47;*.txt"
     *   }
     * }
     *
     * task copy(type: Copy) {
     *   into "$buildDir/copy"
     *   with contentSpec
     * }
     * </pre>
     *
     * @param sourceSpecs The specs to add
     * @return this
     */
    CopySpec with(CopySpec... sourceSpecs);

    // CopySourceSpec overrides to broaden return type

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec from(Object... sourcePaths);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec from(Object sourcePath,
                  @DelegatesTo(CopySpec.class)
                  @ClosureParams(value = SimpleType.class, options = "org.gradle.api.file.CopySpec")
                  Closure c);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec from(Object sourcePath, Action<? super CopySpec> configureAction);

    // PatternFilterable overrides to broaden return type

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec setIncludes(Iterable<String> includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec setExcludes(Iterable<String> excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec include(String... includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec include(Iterable<String> includes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec include(Spec<FileTreeElement> includeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec include(Closure includeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec exclude(String... excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec exclude(Iterable<String> excludes);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec exclude(Spec<FileTreeElement> excludeSpec);

    /**
     * {@inheritDoc}
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Override
    CopySpec exclude(Closure excludeSpec);

    // CopyProcessingSpec overrides to broaden return type

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec into(Object destPath);

    /**
     * Creates and configures a child {@code CopySpec} with the given destination path.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param destPath Path to the destination directory for a Copy
     * @param configureClosure The closure to use to configure the child {@code CopySpec}.
     * @return this
     */
    CopySpec into(Object destPath, @DelegatesTo(CopySpec.class) Closure configureClosure);

    /**
     * Creates and configures a child {@code CopySpec} with the given destination path.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param destPath Path to the destination directory for a Copy
     * @param copySpec The action to use to configure the child {@code CopySpec}.
     * @return this
     */
    CopySpec into(Object destPath, Action<? super CopySpec> copySpec);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec rename(Closure closure);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec rename(Transformer<@Nullable String, String> renamer);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec rename(String sourceRegEx, String replaceWith);

    /**
     * {@inheritDoc}
     */
    @Override
    CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec filter(Class<? extends FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec filter(Closure closure);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec filter(Transformer<@Nullable String, String> transformer);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec expand(Map<String, ?> properties);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec expand(Map<String, ?> properties, Action<? super ExpandDetails> action);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec eachFile(Action<? super FileCopyDetails> action);

    /**
     * {@inheritDoc}
     */
    @Override
    CopySpec eachFile(@DelegatesTo(FileCopyDetails.class) Closure closure);

    /**
     * Gets the charset used to read and write files when filtering.
     * By default, the JVM default charset is used.
     *
     * @return the charset used to read and write files when filtering
     * @since 2.14
     */
    @ToBeReplacedByLazyProperty
    String getFilteringCharset();

    /**
     * Specifies the charset used to read and write files when filtering.
     *
     * @param charset the name of the charset to use when filtering files
     * @since 2.14
     */
    void setFilteringCharset(String charset);
}
