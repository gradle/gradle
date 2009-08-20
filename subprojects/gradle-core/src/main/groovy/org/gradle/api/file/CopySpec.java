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
import org.gradle.api.tasks.util.PatternFilterable;

import java.util.Map;
import java.io.FilterReader;

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
 * @see org.gradle.api.tasks.Copy Copy Task
 * @see org.gradle.api.Project#copy(groovy.lang.Closure) Project.copy()
 */
public interface CopySpec extends CopySourceSpec, CopyProcessingSpec, PatternFilterable {

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
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec setIncludes(Iterable<String> includes);

    /**
     * {@inheritDoc}
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec setExcludes(Iterable<String> excludes);

    /**
     * {@inheritDoc}
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(String... includes);

    /**
     * {@inheritDoc}
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec include(Iterable<String> includes);

    /**
     * {@inheritDoc}
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(String... excludes);

    /**
     * {@inheritDoc}
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    CopySpec exclude(Iterable<String> excludes);


    // CopyProcessingSpec overrides to broaden return type

    /**
     * {@inheritDoc}
     */
    CopySpec into(Object destPath);

    /**
     * {@inheritDoc}
     */
    CopySpec remapTarget(Closure closure);

    /**
     * {@inheritDoc}
     */
    CopySpec rename(String sourceRegEx, String replaceWith);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Class<FilterReader> filterType);

    /**
     * {@inheritDoc}
     */
    CopySpec filter(Closure closure);
}
