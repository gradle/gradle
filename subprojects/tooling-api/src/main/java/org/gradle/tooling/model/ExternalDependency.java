/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.model;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

import java.io.File;

/**
 * Represents an external artifact dependency.
 */
public interface ExternalDependency extends Dependency {
    /**
     * Returns the file for this dependency.
     *
     * @return The file for this dependency.
     */
    File getFile();

    /**
     * Returns the source directory or archive for this dependency, or {@code null} if no source is available.
     *
     * @return The source directory or archive for this dependency, or {@code null} if no source is available.
     */
    @Nullable
    File getSource();

    /**
     * Returns the Javadoc directory or archive for this dependency, or {@code null} if no Javadoc is available.
     *
     * @return the Javadoc directory or archive for this dependency, or {@code null} if no Javadoc is available.
     */
    @Nullable
    File getJavadoc();

    /**
     * Returns the Gradle module information for this dependency, or {@code null} if the dependency does not
     * originate from a remote repository.
     *
     * @return The Gradle module information for this dependency, or {@code null} if the dependency does not
     * originate from a remote repository.
     * @since 1.1
     */
    @Nullable
    @Incubating
    GradleModuleVersion getGradleModuleVersion();
}
