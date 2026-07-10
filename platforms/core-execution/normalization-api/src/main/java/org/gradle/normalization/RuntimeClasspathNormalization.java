/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.normalization;

import org.gradle.api.Action;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configuration of runtime classpath normalization.
 *
 * <p>Several methods accept a file pattern to selectively normalize files.  Patterns may include:</p>
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
 *
 * <pre>
 * all files ending with '.json' (including files in subdirectories)
 *    &#42;&#42;&#47;&#42;.json
 * </pre>
 *
 * <pre>
 * all files beginning with 'build-' in the level1/level2 directory
 *    level1/level2/build-&#42;
 * </pre>
 *
 * <pre>
 * all files (including subdirectories) beneath config/build-data
 *   config/build-data/
 * </pre>
 *
 * <pre>
 * all properties files in a build directory beneath com/acme (including subdirectories)
 *   com/acme/&#42;&#42;&#47;build/&#42;.properties
 * </pre>
 *
 * @since 4.0
 */
@HasInternalProtocol
public interface RuntimeClasspathNormalization extends InputNormalization {
    /**
     * Ignore resources in classpath entries matching {@code pattern}.
     */
    void ignore(String pattern);

    /**
     * Normalize files matching {@code pattern} as properties files, ignoring comments and property order, applying the rules provided by {@code configuration}.
     *
     * @since 6.8
     */
    void properties(String pattern, Action<? super PropertiesFileNormalization> configuration);

    /**
     * Normalize all properties files according to the rules provided by {@code configuration}.  This is equivalent to calling {@link RuntimeClasspathNormalization#properties(String, Action)} with the '**&#47;*.properties' pattern.
     *
     * @since 6.8
     */
    void properties(Action<? super PropertiesFileNormalization> configuration);

    /**
     * Configures the normalization strategy for the {@code META-INF} directory in archives.
     *
     * @since 6.6
     */
    void metaInf(Action<? super MetaInfNormalization> configuration);
}
