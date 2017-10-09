/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;

/**
 * Configuration for a C++ library, defining the source files and header directories that make up the library plus other settings.
 *
 * <p>An instance of this type is added as a project extension by the C++ library plugin.</p>
 *
 * @since 4.2
 */
@Incubating
public interface CppLibrary extends CppComponent {
    /**
     * Defines the public header file directories of this library.
     *
     * <p>When this collection is empty, the directory {@code src/main/public} is used by default.</p>
     */
    ConfigurableFileCollection getPublicHeaders();

    /**
     * Configures the public header directories for this component.
     */
    void publicHeaders(Action<? super ConfigurableFileCollection> action);

    /**
     * Returns the public header file directories of this component, as defined in {@link #getPublicHeaders()}.
     */
    FileCollection getPublicHeaderDirs();

    /**
     * Returns public header files of this component.
     *
     * @since 4.3
     */
    FileTree getPublicHeaderFiles();

    /**
     * Returns the API dependencies of this library.
     */
    Configuration getApiDependencies();

    /**
     * {@inheritDoc}
     */
    @Override
    CppSharedLibrary getDevelopmentBinary();

    /**
     * Returns the debug shared library for this library.
     */
    CppSharedLibrary getDebugSharedLibrary();

    /**
     * Returns the release shared library for this library.
     */
    CppSharedLibrary getReleaseSharedLibrary();
}
