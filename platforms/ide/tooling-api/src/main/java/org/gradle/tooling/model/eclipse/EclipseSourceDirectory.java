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
package org.gradle.tooling.model.eclipse;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.SourceDirectory;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A source directory in an Eclipse project.
 */
public interface EclipseSourceDirectory extends SourceDirectory, EclipseClasspathEntry {
    /**
     * Returns the relative path for this source directory.
     *
     * @return The path for this source directory. Does not return null.
     */
    String getPath();

    /**
     * Returns the include patterns for this source directory.
     *
     * @return The list of patterns to include. Does not return null.
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    List<String> getIncludes() throws UnsupportedMethodException;

    /**
     * Returns the exclude patterns for this source directory.
     *
     * @return The list of patterns to exclude. Does not return null.
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    List<String> getExcludes() throws UnsupportedMethodException;

    /**
     * Returns the output location of this source directory. If {@code null}, then the compiled classes are placed in the project's default output location.
     *
     * @return The output location of this source directory.
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    @Nullable
    String getOutput() throws UnsupportedMethodException;

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    @Override
    DomainObjectSet<? extends ClasspathAttribute> getClasspathAttributes() throws UnsupportedMethodException;
}
