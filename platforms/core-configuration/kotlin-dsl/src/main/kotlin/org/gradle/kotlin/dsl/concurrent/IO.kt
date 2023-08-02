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

import org.gradle.api.Project
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope

import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.useToRun

import java.io.File


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
fun IO.writeFile(file: File, bytes: ByteArray) = io {
    file.writeBytes(bytes)
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
 * A Gradle build service to offload IO actions to a dedicated thread.
 */
@ServiceScope(Scopes.Build::class)
interface AsyncIOScopeFactory {
    fun newScope(): IOScope
}


internal
fun <T> withAsynchronousIO(
    project: Project,
    action: IO.() -> T
): T = project.serviceOf<AsyncIOScopeFactory>().newScope().useToRun(action)


internal
inline fun withSynchronousIO(action: IO.() -> Unit) {
    action(SynchronousIO)
}


internal
object SynchronousIO : IO {
    override fun io(action: () -> Unit) = action()
}
