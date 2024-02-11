/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * This class exposes a number of internal utilities for use by Gradle JVM plugins that use
 * types specific to Java and other JVM languages, such as {@link HasCompileOptions} which are
 * not available in the {@code platform-jvm} project and would otherwise be located on the
 * {@link JvmPluginServices} type.
 */
public interface JvmLanguageUtilities {
    /**
     * Configures a configuration so that its exposed target jvm version is inferred from
     * the specified compilation task.
     *
     * @param configuration the configuration to configure
     * @param compileTask the compile task which serves as reference for inference
     */
    <COMPILE extends AbstractCompile & HasCompileOptions> void useDefaultTargetPlatformInference(Configuration configuration, TaskProvider<COMPILE> compileTask);

    /**
     * Registers a new source directory for a source set, assuming that it will be compiled by
     * a language or compiler for the JVM (aka, it produces .class files).
     * @param sourceSet the source set for which to add a directory
     * @param name the name of the directory
     * @param configuration the configuration of the source directory
     */
    void registerJvmLanguageSourceDirectory(SourceSet sourceSet, String name, Action<? super JvmLanguageSourceDirectoryBuilder> configuration);
}
