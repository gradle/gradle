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

package org.gradle.instantexecution.extensions

import org.gradle.internal.exceptions.MultiCauseException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass


internal
fun Throwable.maybeUnwrapInvocationTargetException() =
    if (this is InvocationTargetException) targetException
    else this


internal
fun Throwable.isOrHasCause(type: KClass<*>): Boolean =
    when {
        type.java.isAssignableFrom(this::class.java) -> true
        this is MultiCauseException -> causes.any { it.isOrHasCause(type) }
        else -> suppressed.any { it.isOrHasCause(type) } || cause?.isOrHasCause(type) == true
    }
