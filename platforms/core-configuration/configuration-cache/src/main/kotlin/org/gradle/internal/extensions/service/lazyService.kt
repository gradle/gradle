/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.extensions.service

import org.gradle.internal.service.LazyService
import kotlin.reflect.KProperty


/**
 * Allows using instances of [LazyService] as delegated properties.
 *
 * ```kotlin
 * class SomeService(myService: LazyService<MyService>) {
 *     private val myService: MyService by myService
 * }
 * ```
 *
 * @see LazyService
 * @see org.gradle.internal.service.ServiceRegistrationProvider
 */
internal
operator fun <T : Any> LazyService<T>.getValue(thisRef: Any?, property: KProperty<*>): T =
    instance
