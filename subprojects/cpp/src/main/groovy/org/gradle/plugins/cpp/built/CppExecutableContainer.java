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
package org.gradle.plugins.cpp.built;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollection;

/**
 * A {@code CppExecutableContainer} manages a set of {@link CppExecutable} objects.
 */
public interface CppExecutableContainer extends NamedDomainObjectCollection<CppExecutable> {

    /**
     * Adds an executable.
     *
     * @param executable The executable to add to this collection.
     * @return The added executable.
     * @throws org.gradle.api.InvalidUserDataException when an executable with the given name already exists in this container.
     */
    CppExecutable add(CppExecutable executable) throws InvalidUserDataException;

}