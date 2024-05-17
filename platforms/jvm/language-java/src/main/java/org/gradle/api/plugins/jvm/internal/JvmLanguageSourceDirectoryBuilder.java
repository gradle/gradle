/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.function.Function;

/**
 * A JVM language source directory builder, which allows the creation
 * of a source directory for an existing source set and associate a compile
 * task which produces classes to it.
 */
public interface JvmLanguageSourceDirectoryBuilder {
    /**
     * The description of the source directory set.
     * If not called Gradle will generate a default description.
     * @param description the description
     */
    JvmLanguageSourceDirectoryBuilder withDescription(String description);

    /**
     * Tells how to compile this source directory set.
     * @param taskBuilder the builder for the task which compiles sources
     */
    JvmLanguageSourceDirectoryBuilder compiledBy(Function<DirectoryProperty, TaskProvider<? extends AbstractCompile>> taskBuilder);

    /**
     * Assumes that this source set will contain Java sources and therefore creates a Java
     * compile task which configuration can be refined using the provided action configuration.
     * Implicitly calls {@link #includeInAllJava()}
     * @param compilerConfiguration the configuration of the compile task
     */
    JvmLanguageSourceDirectoryBuilder compiledWithJava(Action<? super JavaCompile> compilerConfiguration);

    /**
     * Includes this source directory in the "allJava" source set
     */
    JvmLanguageSourceDirectoryBuilder includeInAllJava();
}
