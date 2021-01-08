/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.resolver

import java.io.File
import java.net.URI


/**
 * Environment given to the [KotlinBuildScriptDependenciesResolver] by the Kotlin IntelliJ Plugin.
 *
 * See `GradleScriptTemplateProvider` in `jetbrains/kotlin`.
 */
internal
typealias Environment = Map<String, Any?>


/**
 * Path of the root directory of the IntelliJ project.
 */
internal
val Environment.projectRoot: File
    get() = get("projectRoot") as File


/**
 * Path of the local Gradle installation as configured in IntelliJ.
 */
internal
val Environment.gradleHome: File?
    get() = get("gradleHome") as? File


/**
 * URI of the remote Gradle distribution as configured in IntelliJ.
 */
internal
val Environment.gradleUri: URI?
    get() = get("gradleUri") as? URI


/**
 * Version of Gradle as configured in IntelliJ.
 */
internal
val Environment.gradleVersion: String?
    get() = get("gradleVersion") as? String


/**
 * Path of the Gradle user home as configured in IntelliJ.
 */
internal
val Environment.gradleUserHome: File?
    get() = path("gradleUserHome")


/**
 * JAVA_HOME to use for Gradle as configured in IntelliJ.
 */
internal
val Environment.gradleJavaHome: File?
    get() = path("gradleJavaHome")


/**
 * Gradle options as configured in IntelliJ.
 */
internal
val Environment.gradleOptions: List<String>
    get() = stringList("gradleOptions")


/**
 * Gradle JVM options as configured in IntelliJ.
 */
internal
val Environment.gradleJvmOptions: List<String>
    get() = stringList("gradleJvmOptions")


/**
 * Gradle environment variables as configured in IntelliJ.
 */
internal
val Environment.gradleEnvironmentVariables: Map<String, String>
    get() = stringMap("gradleEnvironmentVariables")


/**
 * Environment flag to enabled short circuiting TAPI resolution when script
 * is changed outside classpath blocks. Defaults to false.
 */
internal
val Map<String, Any?>?.isShortCircuitEnabled: Boolean
    get() = this?.get("gradleKotlinDslScriptDependenciesResolverShortCircuit") == true


private
fun Environment.path(key: String): File? =
    (get(key) as? String)?.let(::File)


@Suppress("unchecked_cast")
private
fun Environment.stringList(key: String): List<String> =
    (get(key) as? List<String>) ?: emptyList()


@Suppress("unchecked_cast")
private
fun Environment.stringMap(key: String) =
    (get(key) as? Map<String, String>) ?: emptyMap()
