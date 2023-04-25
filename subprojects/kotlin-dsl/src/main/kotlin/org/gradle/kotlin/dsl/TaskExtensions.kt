/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.AllTimeService
import org.gradle.api.ExecutionTimeService
import org.gradle.api.Task


inline fun <reified T> Task.getService(): T where T : AllTimeService, T : Task.Service = getService(T::class.java)
inline fun <reified T> Task.getExecutionTimeService(): T where T : ExecutionTimeService, T : Task.Service = getExecutionTimeService(T::class.java)
