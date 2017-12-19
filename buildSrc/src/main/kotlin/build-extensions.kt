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
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*


// This file contains Kotlin extensions for the gradle/gradle build


@Suppress("unchecked_cast")
val Project.libraries
    get() = rootProject.extra["libraries"] as Map<String, Any>


fun Project.library(name: String): Any =
    libraries[name]!!


@Suppress("unchecked_cast")
fun Project.useTestFixtures(project: String? = null, sourceSet: String? = null) =
    (extra["useTestFixtures"] as groovy.lang.Closure<Unit>)(mapOf("project" to project, "sourceSet" to sourceSet))
