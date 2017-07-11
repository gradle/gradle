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

package org.gradle.api.artifacts.component;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * Criteria for selecting a library instance that is built as part of the current build.
 */
@Incubating
public interface LibraryComponentSelector extends ComponentSelector {
    /**
     * Return the project path of the selected library.
     *
     * @return the project path of the library
     */
    String getProjectPath();

    /**
     * Return the library name of the selected library.
     * If the library name is null then it is expected to find a single library defined in same project
     * as the requesting component or dependency resolution will fail.
     * If not <code>null</code> then the name will never be empty.
     *
     * @return the library name
     */
    @Nullable
    String getLibraryName();

    @Nullable
    String getVariant();

}
