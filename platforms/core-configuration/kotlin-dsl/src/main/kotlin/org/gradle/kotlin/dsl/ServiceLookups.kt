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
import org.gradle.api.Task
import org.gradle.api.services.GradleService
import org.gradle.api.services.ProjectService
import org.gradle.api.services.SettingsService
import org.gradle.api.services.TaskService


/**
 * Service lookup available to Kotlin DSL build scripts.
 *
 * Only a curated set of services can be obtained this way, each marked with [ProjectService].
 * To look up a service from a task action, use [Task.service].
 *
 * @since 9.9.0
 */
@Incubating
interface ProjectScriptServiceLookup {

    /**
     * Looks up a service provided by Gradle for use in this build script.
     *
     * @param serviceType the type of the service to look up
     * @return the service instance
     * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to build scripts
     * @since 9.9.0
     */
    fun <T : ProjectService> service(serviceType: Class<T>): T
}


/**
 * Looks up a service provided by Gradle for use in this build script.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to build scripts
 * @see ProjectScriptServiceLookup
 * @since 9.9.0
 */
@Incubating
inline fun <reified T : ProjectService> ProjectScriptServiceLookup.service(): T =
    service(T::class.java)


/**
 * Service lookup available to Kotlin DSL settings scripts.
 *
 * Only a curated set of services can be obtained this way, each marked with [SettingsService].
 *
 * @since 9.9.0
 */
@Incubating
interface SettingsScriptServiceLookup {

    /**
     * Looks up a service provided by Gradle for use in this settings script.
     *
     * @param serviceType the type of the service to look up
     * @return the service instance
     * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to settings scripts
     * @since 9.9.0
     */
    fun <T : SettingsService> service(serviceType: Class<T>): T
}


/**
 * Looks up a service provided by Gradle for use in this settings script.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to settings scripts
 * @see SettingsScriptServiceLookup
 * @since 9.9.0
 */
@Incubating
inline fun <reified T : SettingsService> SettingsScriptServiceLookup.service(): T =
    service(T::class.java)


/**
 * Service lookup available to Kotlin DSL init scripts.
 *
 * Only a curated set of services can be obtained this way, each marked with [GradleService].
 *
 * @since 9.9.0
 */
@Incubating
interface InitScriptServiceLookup {

    /**
     * Looks up a service provided by Gradle for use in this init script.
     *
     * @param serviceType the type of the service to look up
     * @return the service instance
     * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to init scripts
     * @since 9.9.0
     */
    fun <T : GradleService> service(serviceType: Class<T>): T
}


/**
 * Looks up a service provided by Gradle for use in this init script.
 *
 * @param T the type of the service to look up
 * @return the service instance
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to init scripts
 * @see InitScriptServiceLookup
 * @since 9.9.0
 */
@Incubating
inline fun <reified T : GradleService> InitScriptServiceLookup.service(): T =
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
 * @throws org.gradle.api.InvalidUserDataException when the given type is not one of the services available to tasks
 * @see [Task.service]
 * @since 9.9.0
 */
@Incubating
inline fun <reified T : TaskService> Task.service(): T =
    service(T::class.java)
