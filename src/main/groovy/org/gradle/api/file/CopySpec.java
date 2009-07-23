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
package org.gradle.api.file;

import groovy.lang.Closure;

import java.io.FilterReader;
import java.util.Map;

/**
 * A set of specifications for copying files.  This includes:
 * <ul>
 *   <li>source directories (multiples allowed)
 *   <li>destination directory
 *   <li>ANT like include patterns
 *   <li>ANT like exclude patterns
 *   <li>File relocating rules
 *   <li>renaming rules
 *   <li>content filters
 * </ul>
 *
 * CopySpecs may be nested by passing a closure to one of the from methods.  The
 * closure creates a child CopySpec and delegates methods in the closure to the child.
 * Child CopySpecs inherit any values specified in the parent.  Only the leaf CopySpecs
 * will be used in any copy operations.
 * This allows constructs like:
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
 * In this example, the <code>into</code> and <code>exclude</code> specifications at the
 * root level are inherited by the two child CopySpecs. 
 * @author Steve Appling
 */
public interface CopySpec {
    /**
     * Specifies sources for a copy.
     * The toString() method of each sourcePath is used to get a path.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * Relative paths will be evaluated relative to the project directory.
     * @param sourcePaths Paths to source directories for the copy
     */
    CopySpec from(Object... sourcePaths);

    /**
     * Specifies the source for a copy and creates a child CopySpec.
     * SourcePath.toString is used as the path.
     * The source is set on the child CopySpec, not on this one.
     * This may be a path to a single file to copy or to a directory.  If the path is to a directory,
     * then the contents of the directory will be copied.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePath Path to source for the copy
     * @param c closure for configuring the child CopySpec
     */
    CopySpec from(Object sourcePath, Closure c);

    /**
     * Specifies sources for a copy.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePaths Paths to source directories for the copy
     */
    CopySpec from(Iterable<Object> sourcePaths);

    /**
     * Specifies sources for a copy and creates a child
     * CopySpec which is configured with the Closure. The sources are
     * set on the child CopySpec, not on this one.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePaths Paths to source directories for the copy
     * @param c Closure for configuring the child CopySpec
     */
    CopySpec from(Iterable<Object> sourcePaths, Closure c);

    /**
     * Specifies the destination directory for a copy.
     * The path is evaluated relative to the project directory.
     * @param destPath Path to the destination directory for a Copy
     */
    CopySpec into(Object destPath);

    /**
     * Adds an ANT style include pattern to the copy specification.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
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
     * all files beneath any CVS directory (including subdirectories) under src/main/java
     *   src/main/java/**&#47;CVS/**
     * </pre>
     *
     * If this method is not called, then all files beneath the source directory will be included.
     * If this method is called, then a file must match at least one of the include
     * patterns to be copied.
     * @param includes a vararg list of include patterns
     */
    CopySpec include(String ... includes);

    /**
     * Adds an ANT style exclude pattern to the copy specification.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     * See {@link #include(String[]) include} for a description of the
     * syntax for patterns.
     *
     * If this method is not called, then no files will be excluded.
     * If this method is called, then files must not match any exclude pattern
     * to be copied.
     *
     * @param excludes a vararg list of exclude patterns
     */
    CopySpec exclude(String ... excludes);

    /**
     * Maps a source file to a different relative location under the target directory.
     * The closure will be called with a single parameter, the File object
     * for the default location of the copy.  This File will have the same relative path
     * from the destination directory that the source file has from its source
     * directory.  The closure should return a File object with a new target destination.
     * @param closure remap closure
     */
    CopySpec remapTarget(Closure closure);

    /**
     * Renames files based on a regular expression.  Uses java.util.regex type of
     * regular expressions.  Note that the replace string should use the '$1' syntax
     * to refer to capture groups in the source regular expression.  Files that
     * do not match the source regular expression will be copied with the original name.
     *
     * <p>
     * Example:
     * <pre>
     * rename '(.*)_OEM_BLUE_(.*)', '$1$2'
     * </pre>
     * would map the file 'style_OEM_BLUE_.css' to 'style.css'
     * @param sourceRegEx Source regular expression
     * @param replaceWith Replacement string (use $ syntax for capture groups)
     */
    CopySpec rename(String sourceRegEx, String replaceWith);


    /**
     * Adds a content filter to be used during the copy.  Multiple calls to
     * filter, add additional filters to the filter chain.  Each filter should implement
     * java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters.
     * <p>
     * Filter parameters may be specified using groovy map syntax.
     * <p>
     * Examples:
     * <pre>
     *    filter(HeadFilter, lines:25, skip:2)
     *    filter(ReplaceTokens, tokens:[copyright:'2009', version:'2.3.1'])
     * </pre>
     * @param map map of filter parameters
     * @param filterType Class of filter to add
     */
    CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType);

    /**
     * Adds a content filter to be used during the copy.  Multiple calls to
     * filter, add additional filters to the filter chain.  Each filter should implement
     * java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters.
     * <p>
     * Examples:
     * <pre>
     *    filter(StripJavaComments)
     *    filter(com.mycompany.project.CustomFilter)
     * </pre>
     * @param filterType Class of filter to add
     */
    CopySpec filter(Class<FilterReader> filterType);
}
