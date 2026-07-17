/*
 * Copyright 2026 the original author or authors.
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

@file:Incubating

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle


/**
 * Looks up a service provided by Gradle for use in this project.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available in this scope
 * @see [Project.service]
 * @since 9.8.0
 */
@Incubating
inline fun <reified T : Any> Project.service(): T =
    service(T::class.java)


/**
 * Looks up a service provided by Gradle for use in this task.
 *
 * The lookup can be used both at configuration time and from task actions at execution time,
 * and is safe to use with the configuration cache:
 *
 * ```kotlin
 * tasks.register("cleanThing") {
 *     doLast {
 *         service<FileSystemOperations>().delete {
 *             delete("thing")
 *         }
 *     }
 * }
 * ```
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available in this scope
 * @see [Task.service]
 * @since 9.8.0
 */
@Incubating
inline fun <reified T : Any> Task.service(): T =
    service(T::class.java)


/**
 * Looks up a service provided by Gradle for use in this build.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available in this scope
 * @see [Settings.service]
 * @since 9.8.0
 */
@Incubating
inline fun <reified T : Any> Settings.service(): T =
    service(T::class.java)


/**
 * Looks up a service provided by Gradle for use in init scripts and [Gradle] plugins.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available in this scope
 * @see [Gradle.service]
 * @since 9.8.0
 */
@Incubating
inline fun <reified T : Any> Gradle.service(): T =
    service(T::class.java)
