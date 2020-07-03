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
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.extra


// TODO these extensions should go with https://github.com/gradle/gradle-private/issues/3098


@Suppress("unchecked_cast")
val Project.libraries
    get() = if (rootProject.extra.has("libraries")) rootProject.extra["libraries"] as Map<String, Map<String, String>> else mapOf()


@Suppress("unchecked_cast")
val Project.testLibraries
    get() = if (rootProject.extra.has("testLibraries")) rootProject.extra["testLibraries"] as Map<String, Any> else mapOf()


fun Project.library(name: String): String =
    libraries.getValue(name).getValue("coordinates")


fun Project.libraryVersion(name: String): String =
    libraries.getValue(name).getValue("version")


fun Project.libraryReason(name: String): String? =
    libraries.getValue(name)["because"]


fun Project.testLibrary(name: String): String =
    testLibraries.getValue(name) as String


@Suppress("unchecked_cast")
fun Project.testLibraries(name: String): List<String> =
    testLibraries.getValue(name) as List<String>


fun RepositoryHandler.googleApisJs() {
    ivy {
        name = "googleApisJs"
        setUrl("https://ajax.googleapis.com/ajax/libs")
        patternLayout {
            artifact("[organization]/[revision]/[module].[ext]")
            ivy("[organization]/[revision]/[module].xml")
        }
        metadataSources {
            artifact()
        }
    }
}
