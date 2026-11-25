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

package org.gradle.external.javadoc;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link CoreJavadocOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface CoreJavadocOptionsInputs {

    /**
     * The lazy value of the {@code -quiet} or {@code -verbose} output level.
     *
     * @see CoreJavadocOptions#getOutputLevel()
     * @since 10.0.0
     */
    Property<JavadocOutputLevel> getOutputLevel();

    /**
     * The lazy value of the lowest visibility of the documented members.
     *
     * @see CoreJavadocOptions#getMemberLevel()
     * @since 10.0.0
     */
    Property<JavadocMemberLevel> getMemberLevel();

    /**
     * The lazy value of the {@code -overview} file.
     *
     * @see CoreJavadocOptions#getOverview()
     * @since 10.0.0
     */
    Property<String> getOverview();

    /**
     * The lazy value of the {@code -doclet} class.
     *
     * @see CoreJavadocOptions#getDoclet()
     * @since 10.0.0
     */
    Property<String> getDoclet();

    /**
     * The lazy value of the {@code -docletpath}.
     *
     * @see CoreJavadocOptions#getDocletpath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getDocletpath();

    /**
     * The lazy value of the {@code -source} release.
     *
     * @see CoreJavadocOptions#getSource()
     * @since 10.0.0
     */
    Property<String> getSource();

    /**
     * The lazy value of the {@code -classpath}.
     *
     * @see CoreJavadocOptions#getClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getClasspath();

    /**
     * The lazy value of the {@code --module-path}.
     *
     * @see CoreJavadocOptions#getModulePath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getModulePath();

    /**
     * The lazy value of the {@code -bootclasspath}.
     *
     * @see CoreJavadocOptions#getBootClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getBootClasspath();

    /**
     * The lazy value of the {@code -extdirs}.
     *
     * @see CoreJavadocOptions#getExtDirs()
     * @since 10.0.0
     */
    ConfigurableFileCollection getExtDirs();

    /**
     * The lazy value of whether {@code -breakiterator} is used.
     *
     * @see CoreJavadocOptions#isBreakIterator()
     * @since 10.0.0
     */
    Property<Boolean> getBreakIterator();

    /**
     * The lazy value of the {@code -locale}.
     *
     * @see CoreJavadocOptions#getLocale()
     * @since 10.0.0
     */
    Property<String> getLocale();

    /**
     * The lazy value of the {@code -encoding} of the source files.
     *
     * @see CoreJavadocOptions#getEncoding()
     * @since 10.0.0
     */
    Property<String> getEncoding();

    /**
     * The lazy value of the {@code -J} flags of the Javadoc JVM.
     *
     * @see CoreJavadocOptions#getJFlags()
     * @since 10.0.0
     */
    ListProperty<String> getJFlags();

    /**
     * The lazy value of the additional {@code @} option files.
     *
     * @see CoreJavadocOptions#getOptionFiles()
     * @since 10.0.0
     */
    ConfigurableFileCollection getOptionFiles();
}
