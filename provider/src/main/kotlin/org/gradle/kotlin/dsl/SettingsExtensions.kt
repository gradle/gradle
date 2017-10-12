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
package org.gradle.kotlin.dsl

import org.gradle.api.initialization.Settings

/**
 * Adds the given projects to the build.
 *
 * @param projectPaths the projects to add
 * @see [Settings.include]
 */
fun Settings.include(vararg projectPaths: String) {
    include(projectPaths)
}

/**
 * Adds the given projects to the build.
 *
 * @param projectNames the projects to add
 * @see [Settings.includeFlat]
 */
fun Settings.includeFlat(vararg projectNames: String) {
    includeFlat(projectNames)
}
