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

package org.gradle.tooling.model.cpp;

import org.gradle.tooling.model.DomainObjectSet;

/**
 * Represents a C++ component.
 *
 * @since 4.10
 */
public interface CppComponent {
    /**
     * Returns the name of this component. This is used to disambiguate the component of a project. Each component has a unique name within its project. However, these names are not unique across multiple projects.
     */
    String getName();

    /**
     * All binaries buildable for this component. These will implement {@link CppExecutable}, {@link CppSharedLibrary} or {@link CppStaticLibrary}.
     */
    DomainObjectSet<? extends CppBinary> getBinaries();

    /**
     * Returns the base name of this component.
     */
    String getBaseName();
}
