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

package org.gradle.api.tasks.compile;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceTaskInputs;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link AbstractCompile}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface AbstractCompileInputs extends SourceTaskInputs {

    /**
     * The classpath to use to compile the source files.
     *
     * @see AbstractCompile#getClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getClasspath();

    /**
     * The Java language level to use to compile the source files.
     *
     * @see AbstractCompile#getSourceCompatibility()
     * @since 10.0.0
     */
    Property<String> getSourceCompatibility();

    /**
     * The target JVM to generate the {@code .class} files for.
     *
     * @see AbstractCompile#getTargetCompatibility()
     * @since 10.0.0
     */
    Property<String> getTargetCompatibility();
}
