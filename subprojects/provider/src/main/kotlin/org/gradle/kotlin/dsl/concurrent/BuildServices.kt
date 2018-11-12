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

import java.io.File

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


internal
object BuildServices {

    @Suppress("unused")
    fun createAsyncIOScopeFactory(executorFactory: ExecutorFactory): AsyncIOScopeFactory =
        AsyncIOScopeFactory { executorFactory.create("Kotlin DSL Writer", 1) }
}


/**
 * A scheduler of IO actions.
 */
interface IO {

    /**
     * Schedules the given [io action][action] for execution.
     *
     * The effect of an IO [action] is only guaranteed to be observable
     * by a subsequent [io] action or after the [closing][IOScope.close] of
     * the current [IOScope].
     */
    fun io(action: () -> Unit)
}


/**
 * Schedules the writing of the given [file].
 */
fun IO.writeFile(file: File, bytes: ByteArray) {
    io { file.writeBytes(bytes) }
}


/**
 * A scope for the scheduling of IO actions.
 *
 * [close] guarantees all scheduled IO actions are executed before returning.
 *
 * Each [IOScope] operates independently and failures in one [IOScope]
 * do not affect existing or future [IOScope]s.
 *
 * Each [IOScope] can only be used from a single thread at a time.
 */
interface IOScope : IO, AutoCloseable


/**
 * Creates [IOScope] instances that offload IO actions to a dedicated [ExecutorService][executorService].
 */
internal
class AsyncIOScopeFactory(
    executorServiceProvider: () -> ExecutorService
) : AutoCloseable {

    private
    val executorService = lazy(executorServiceProvider)

    fun newScope(): IOScope = object : IOScope {

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
            pending?.get(30, TimeUnit.SECONDS)
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
