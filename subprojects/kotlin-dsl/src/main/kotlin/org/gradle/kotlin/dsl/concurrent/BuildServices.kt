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

package org.gradle.kotlin.dsl.concurrent

import org.gradle.internal.concurrent.ExecutorFactory

import java.io.Closeable
import java.time.Duration

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


internal
object BuildServices {

    @Suppress("unused")
    fun createAsyncIOScopeFactory(executorFactory: ExecutorFactory): AsyncIOScopeFactory =
        DefaultAsyncIOScopeFactory { executorFactory.create("Kotlin DSL Writer", 1) }
}


internal
interface AsyncIOScopeSettings {
    val ioActionTimeoutMs: Long
}


internal
class JavaSystemPropertiesAsyncIOScopeSettings : AsyncIOScopeSettings {

    companion object {
        const val IO_ACTION_TIMEOUT_SYSTEM_PROPERTY = "org.gradle.kotlin.dsl.internal.io.timeout"
        val DEFAULT_IO_ACTION_TIMEOUT = Duration.ofMinutes(1).toMillis()
    }

    override val ioActionTimeoutMs: Long by lazy {
        System.getProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY)
            .takeIf { !it.isNullOrBlank() }
            ?.let { property ->
                property.toPositiveLongOrNull()
                    ?: throw Exception(
                        "Invalid value for system property '$IO_ACTION_TIMEOUT_SYSTEM_PROPERTY': '$property'. It must be a positive number of milliseconds."
                    )
            }
            ?: DEFAULT_IO_ACTION_TIMEOUT
    }

    private
    fun String.toPositiveLongOrNull() =
        toLongOrNull()?.takeIf { it > 0 }
}


internal
class DefaultAsyncIOScopeFactory(
    private
    val settings: AsyncIOScopeSettings = JavaSystemPropertiesAsyncIOScopeSettings(),
    executorServiceProvider: () -> ExecutorService
) : Closeable, AsyncIOScopeFactory {

    private
    val executorService = lazy(executorServiceProvider)

    override fun newScope(): IOScope = object : IOScope {

        private
        val failure = AtomicReference<Throwable?>(null)

        private
        var pending: Future<*>? = null

        override fun io(action: () -> Unit) {
            // Fail fast
            checkForFailure()
            pending = submit(action)
        }

        override fun close() {
            pending?.get(settings.ioActionTimeoutMs, TimeUnit.MILLISECONDS)
            checkForFailure()
        }

        private
        fun submit(action: () -> Unit): Future<*> =
            executorService.value.submit {
                try {
                    action()
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }

        private
        fun checkForFailure() {
            failure.getAndSet(null)?.let { throw it }
        }
    }

    override fun close() {
        executorService.apply {
            if (isInitialized()) {
                value.shutdown()
            }
        }
    }
}
