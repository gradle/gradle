/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.extensions.core

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.service.ServiceRegistry


internal
inline fun <reified T : Any> TaskInternal.serviceOf(): T =
    project.serviceOf()


inline fun <reified T : Any> Project.serviceOf(): T =
    (this as ProjectInternal).services.get()


inline fun <reified T : Any> GradleInternal.serviceOf(): T =
    services.get()


inline fun <reified T : Any> ServiceRegistry.get(): T =
    this[T::class.java]
