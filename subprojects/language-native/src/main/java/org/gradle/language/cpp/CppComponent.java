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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.language.BinaryCollection;
import org.gradle.language.ComponentWithBinaries;
import org.gradle.language.ComponentWithDependencies;
import org.gradle.language.ComponentWithTargetMachines;

/**
 * Configuration for a C++ component, such as a library or executable, defining the source files and private header directories that make up the component. Private headers are those that are visible only to the source files of the component.
 *
 * <p>A C++ component is composed of some C++ source files that are compiled and then linked into some binary.</p>
 *
 * <p>An instance of this type is added as a project extension by the C++ plugins.</p>
 *
 * @since 4.2
 */
public interface CppComponent extends ComponentWithBinaries, ComponentWithDependencies, ComponentWithTargetMachines {
    /**
     * Specifies the base name for this component. This name is used to calculate various output file names. The default value is calculated from the project name.
     */
    Property<String> getBaseName();

    /**
     * Defines the source files or directories of this component. You can add files or directories to this collection. When a directory is added, all source files are included for compilation.
     *
     * <p>When this collection is empty, the directory {@code src/main/cpp} is used by default.</p>
     */
    ConfigurableFileCollection getSource();

    /**
     * Configures the source files or directories for this component.
     */
    void source(Action<? super ConfigurableFileCollection> action);

    /**
     * Returns the C++ source files of this component, as defined in {@link #getSource()}.
     */
    FileCollection getCppSource();

    /**
     * Defines the private header file directories of this library.
     *
     * <p>When this collection is empty, the directory {@code src/main/headers} is used by default.</p>
     */
    ConfigurableFileCollection getPrivateHeaders();

    /**
     * Configures the private header directories for this component.
     */
    void privateHeaders(Action<? super ConfigurableFileCollection> action);

    /**
     * Returns the private header include directories of this component, as defined in {@link #getPrivateHeaders()}.
     */
    FileCollection getPrivateHeaderDirs();

    /**
     * Returns all header files of this component. Includes public and private header files.
     */
    FileTree getHeaderFiles();

    /**
     * Returns the implementation dependencies of this component.
     */
    Configuration getImplementationDependencies();

    /**
     * Returns the binaries for this library.
     *
     * @since 4.5
     */
    @Override
    BinaryCollection<? extends CppBinary> getBinaries();
}
