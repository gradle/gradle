/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.model.idea;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.java.JavaRuntime;

/**
 * Describes Java source settings for an IDEA module.
 *
 * @since 2.11
 */
@Incubating
public interface IdeaJavaSettings {

    /**
     * Returns the Java source language level.
     *
     * @return The source language level, or {@code null} if this value should be inherited.
     */
    JavaVersion getSourceLanguageLevel();

    /**
     * Returns the target bytecode level.
     *
     * @return The target bytecode language level. Never returns {@code null}.
     */
    JavaVersion getTargetBytecodeLevel();

    /**
     * Returns the target Java runtime.
     *
     * @return The target Java runtime. Never returns {@code null}.
     */
    JavaRuntime getTargetRuntime();

    /**
     * Returns {@code true} if the bytecode level is inherited from the project settings.
     *
     * @return whether the target bytecode level is inherited
     */
    boolean isTargetBytecodeLevelInherited();

    /**
     * Returns {@code true} if the target runtime setting for the idea module is inherited from the project source settings.
     *
     * @return whether the target runtime is inherited
     */
    boolean isTargetRuntimeInherited();
}
