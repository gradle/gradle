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
    get() = rootProject.extra["libraries"] as Map<String, Map<String, String>>


@Suppress("unchecked_cast")
val Project.testLibraries
    get() = rootProject.extra["testLibraries"] as Map<String, Any>


fun Project.library(name: String): String =
    libraries[name]!!["coordinates"]!!


fun Project.libraryVersion(name: String): String =
    libraries[name]!!["version"]!!


fun Project.libraryReason(name: String): String? =
    libraries[name]!!["because"]


fun Project.testLibrary(name: String): Any =
    testLibraries[name]!!


// TODO:kotlin-dsl Remove work around for https://github.com/gradle/kotlin-dsl/issues/639 once fixed
@Suppress("unchecked_cast")
fun Project.testLibraries(name: String): List<Any> =
    testLibraries[name]!! as List<Any>


val Project.maxParallelForks: Int
    get() {
        return ifProperty("maxParallelForks",
            findProperty("maxParallelForks")?.let { Integer.valueOf(it.toString(), 10) }) ?: 4
    }


val Project.useAllDistribution: Boolean
    get() {
        return ifProperty("useAllDistribution", true) ?: false
    }


private
fun <T> Project.ifProperty(name: String, then: T): T? =
    then.takeIf { rootProject.findProperty(name) == true }
