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

package org.gradle.platform.base;

import org.gradle.api.Incubating;

/**
 * A dependency onto a specific binary of a library published by a project.
 */
@Incubating
public interface LibraryBinaryDependencySpec extends DependencySpec {

    /**
     * Returns the project path of the project this dependency refers to.
     *
     * @return the project path
     */
    String getProjectPath();

    /**
     * Returns the name of the library this dependency refers to. If null, it should be assumed that the project
     * defines a single library.
     *
     * @return the library name
     */
    String getLibraryName();

    /**
     * Returns the variant of this binary.
     *
     * @return the library variant
     */
    String getVariant();
}
