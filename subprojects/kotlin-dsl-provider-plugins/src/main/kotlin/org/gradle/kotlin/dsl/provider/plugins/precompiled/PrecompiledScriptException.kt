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

package org.gradle.kotlin.dsl.provider.plugins.precompiled

import org.gradle.api.GradleException
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.LocationAwareException
import java.lang.reflect.InvocationTargetException


@Contextual
class PrecompiledScriptException
internal constructor(message: String, cause: Throwable? = null) : GradleException(message, reduceCause(cause))


private
fun reduceCause(cause: Throwable?): Throwable? =
    when (cause) {
        is InvocationTargetException -> reduceCause(cause.targetException)
        is LocationAwareException -> if (cause.location == null && cause.cause != null) reduceCause(cause.cause!!) else cause
        else -> cause
    }
