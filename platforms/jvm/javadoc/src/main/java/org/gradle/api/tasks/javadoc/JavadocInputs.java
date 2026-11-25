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

package org.gradle.api.tasks.javadoc;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceTaskInputs;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link Javadoc}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface JavadocInputs extends SourceTaskInputs {

    /**
     * The amount of memory allocated to the Javadoc process.
     *
     * @see Javadoc#getMaxMemory()
     * @since 10.0.0
     */
    Property<String> getMaxMemory();

    /**
     * The title of the generated documentation.
     *
     * @see Javadoc#getTitle()
     * @since 10.0.0
     */
    Property<String> getTitle();

    /**
     * The classpath used to locate classes referenced by the documented sources.
     *
     * @see Javadoc#getClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getClasspath();

    /**
     * Whether the build fails when Javadoc fails.
     *
     * @see Javadoc#isFailOnError()
     * @since 10.0.0
     */
    Property<Boolean> getFailOnError();

    /**
     * The Javadoc executable.
     *
     * @see Javadoc#getExecutable()
     * @since 10.0.0
     */
    Property<String> getExecutable();
}
