/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer


/**
 * Creates a [Task] with the given [name] and type, passing the given arguments to the [javax.inject.Inject]-annotated constructor,
 * and adds it to this project tasks container.
 */
inline fun <reified T : Task> TaskContainer.create(name: String, vararg arguments: Any) =
    create(name, T::class.java, *arguments)
