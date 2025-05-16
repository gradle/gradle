/*
 * Copyright 2025 the original author or authors.
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

@file:Suppress("DEPRECATION")
package org.gradle.kotlin.dsl

import org.gradle.api.plugins.Convention
import kotlin.reflect.KClass

@Deprecated("should only be invoked by Gradle versions older than 8.2", level =  DeprecationLevel.HIDDEN)
fun <T : Any> Convention.findPlugin(conventionType: KClass<T>): T? =
    findPlugin(conventionType.java)
