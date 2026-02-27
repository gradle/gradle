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
package org.gradle.platform.base;

import org.gradle.api.Incubating;

/**
 * A builder of a {@link ProjectDependencySpec}.
 */
@Incubating
public interface ProjectDependencySpecBuilder extends DependencySpecBuilder {

    /**
     * Narrows this dependency specification down to a specific project.
     *
     * @param path the project path
     *
     * @return this instance
     */
    ProjectDependencySpecBuilder project(String path);

    /**
     * Narrows this dependency specification down to a specific library.
     *
     * @param name the library name
     *
     * @return this instance
     */
    ProjectDependencySpecBuilder library(String name);
}
