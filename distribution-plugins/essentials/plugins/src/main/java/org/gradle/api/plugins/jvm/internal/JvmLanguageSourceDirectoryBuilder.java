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
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;
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
    JvmLanguageSourceDirectoryBuilder compiledBy(Action<? super CompileTaskDetails> taskBuilder);

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

    /**
     * A builder for compilation tasks.
     */
    interface CompileTaskDetails {
        /**
         * Returns the source directory of the compile task: this is the directory
         * registered automatically by Gradle when constructing the source directory set.
         */
        DirectoryProperty getSourceDirectory();

        /**
         * Sets the compile task for this source directory. You must also set the
         * directory provider for the generated classes (it's expected that this output
         * directory property is found on the task itself, for example {@link JavaCompile#getDestinationDirectory()}.
         *
         * @param task a compilation task
         * @param mapping the mapping from the task to its classes output directory
         * @param <T> the type of the compile task
         */
        <T extends Task> void setCompileTask(TaskProvider<? extends Task> task, Function<T, DirectoryProperty> mapping);
    }
}
