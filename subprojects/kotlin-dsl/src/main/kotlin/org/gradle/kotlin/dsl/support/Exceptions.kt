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

package org.gradle.kotlin.dsl.support

import kotlin.reflect.KClass


fun illegalElementType(container: Any, name: String, expectedType: KClass<*>, actualType: KClass<*>) =
    IllegalArgumentException(
        "Element '$name' of type '${actualType.java.name}' from container '$container' cannot be cast to '${expectedType.qualifiedName}'.")


internal
fun internalError(): Nothing =
    throw InternalError("This should not happen, please report at https://github.com/gradle/kotlin-dsl/issues/new")
