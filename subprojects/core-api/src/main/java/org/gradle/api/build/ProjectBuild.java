/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.build;

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Provides access to the information about the build containing the project.
 *
 * @since 8.11
 */
@Incubating
@ServiceScope(Scope.Project.class)
public interface ProjectBuild {

    /**
     * Returns the root directory of the build.
     * <p>
     * The root directory is the project directory of the root project.
     *
     * @since 8.11
     */
    @Incubating
    Directory getRootDirectory();

}
