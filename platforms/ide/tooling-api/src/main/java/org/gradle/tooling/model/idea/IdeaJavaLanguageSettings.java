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

import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.java.InstalledJdk;

/**
 * Describes Java language settings for an IDEA module.
 *
 * @since 2.11
 */
public interface IdeaJavaLanguageSettings {

    /**
     * Returns the Java language level.
     *
     * @return The language level, or {@code null} if this value should be inherited.
     */
    JavaVersion getLanguageLevel();

    /**
     * Returns the target bytecode level.
     *
     * @return The target bytecode language level, or {@code null} if this value should be inherited.
     * @throws UnsupportedMethodException For provider Gradle versions older than 2.11, where this method is not supported.
     */
    JavaVersion getTargetBytecodeVersion() throws UnsupportedMethodException;

    /**
     * Returns the JDK used for building.
     *
     * @return The JDK, or {@code null} if this value should be inherited.
     * @throws UnsupportedMethodException For provider Gradle versions older than 2.11, where this method is not supported.
     */
    InstalledJdk getJdk() throws UnsupportedMethodException;
}
