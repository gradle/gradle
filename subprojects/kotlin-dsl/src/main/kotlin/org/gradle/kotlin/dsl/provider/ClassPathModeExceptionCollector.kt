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
package org.gradle.kotlin.dsl.provider

import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.ExceptionCollector


@Deprecated("Scheduled to be removed in Gradle 8.0", ReplaceWith("org.gradle.internal.service.scopes.ExceptionCollector"))
open class ClassPathModeExceptionCollector(private val delegate: ExceptionCollector) : Stoppable {

    val exceptions: List<Exception>
        get() = delegate.exceptions

    fun collect(error: Exception) {
        delegate.addException(error)
    }

    override fun stop() {
        delegate.stop()
    }
}


@Suppress("unused", "deprecation")
@Deprecated("Scheduled to be removed in Gradle 8.0")
inline fun <T> ClassPathModeExceptionCollector.ignoringErrors(f: () -> T): T? =
    try {
        f()
    } catch (e: Exception) {
        collect(e)
        null
    }
