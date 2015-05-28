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
import org.gradle.api.Nullable;

/**
 * Criteria for selecting a library instance that is built as part of the current build.
 *
 */
@Incubating
public interface LibraryComponentSelector extends ComponentSelector {
    /**
     * Return the project path of the selected library. If the project path is null then
     * it is expected to find the library in the current project. It is not possible to
     * have both {@link #getProjectPath()} and {@link #getLibraryName()} to return null
     * at the same time: at least one of them has to be non null.
     *
     * @return the project path of the library
     */
    @Nullable
    String getProjectPath();

    /**
     * Return the library name of the selected library. If the library name is null then
     * it is expected to find single library defined in the current project. It is not possible to
     * have both {@link #getProjectPath()} and {@link #getLibraryName()} to return null
     * at the same time: at least one of them has to be non null.
     *
     * @return the library name
     */
    @Nullable
    String getLibraryName();
}
