/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.model.eclipse;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.java.InstalledJdk;

/**
 * Describes Eclipse Java source settings for compiling and running some Java source code.
 *
 * @since 2.10
 */
@Incubating
public interface EclipseJavaSourceSettings {
    /**
     * Returns the Java source language level.
     *
     * @return The source language level. Never returns {@code null}.
     */
    JavaVersion getSourceLanguageLevel();

    /**
     * Returns the target bytecode level.
     *
     * @return The target bytecode language version. Never returns {@code null}.
     * @throws UnsupportedMethodException For Gradle versions older than 2.11, where this method is not supported.
     * @since 2.11
     */
    JavaVersion getTargetBytecodeVersion() throws UnsupportedMethodException;

    /**
     * Returns the JDK used for building.
     *
     * @return The JDK. Never returns {@code null}.
     * @throws UnsupportedMethodException For provider Gradle versions older than 2.11, where this method is not supported.
     * @since 2.11
     */
    InstalledJdk getJdk() throws UnsupportedMethodException;
}
