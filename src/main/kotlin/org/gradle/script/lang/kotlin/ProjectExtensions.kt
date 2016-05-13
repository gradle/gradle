/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import kotlin.reflect.KClass

fun Project.applyPlugin(pluginClass: KClass<out Plugin<Project>>) = pluginManager.apply(pluginClass.java)

inline fun <reified T : Plugin<Project>> Project.apply() = applyPlugin(T::class)

inline fun <reified T : Any> Project.configure(configuration: T.() -> Unit) {
    configure(T::class, configuration)
}

inline fun <T : Any> Project.configure(extensionType: KClass<T>, configuration: T.() -> Unit) {
    configuration(convention.getPlugin(extensionType.java))
}

inline fun <reified T : Task> Project.task(name: String, noinline configuration: T.() -> Unit) =
    task(name, T::class, configuration)

inline fun <reified T : Task> Project.task(name: String) =
    tasks.create(name, T::class.java)

fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    createTask(name, type, configuration)

fun Project.task(name: String, configuration: Task.() -> Unit) =
    createTask(name, DefaultTask::class, configuration)

fun <T : Task> Project.createTask(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    tasks.create(name, type.java, configuration)
