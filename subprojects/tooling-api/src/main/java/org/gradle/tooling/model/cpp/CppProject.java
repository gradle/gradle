/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.tooling.model.ProjectModel;

import javax.annotation.Nullable;

/**
 * Details about the C++ components of a Gradle project.
 *
 * @since 4.10
 */
public interface CppProject extends ProjectModel {
    /**
     * Returns the main C++ component of this project, if any.
     *
     * @return the main component or {@code null} when the project does not have a main component. The component will implement either {@link CppApplication} or {@link CppLibrary}.
     */
    @Nullable
    CppComponent getMainComponent();

    /**
     * Returns the C++ unit test suite of this project, if any.
     *
     * @return the test suite or {@code null} when the project does not have a unit test suite.
     */
    @Nullable
    CppTestSuite getTestComponent();
}
