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

package org.gradle.kotlin.dsl.concurrent

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine


/**
 * Universal Tooling API wrapper.
 *
 * Suspends on a TAPI [computation] until it completes on the given [ResultHandler].
 *
 * Execution will continue on the TAPI executor thread.
 */
internal inline
suspend fun <T> tapi(crossinline computation: (ResultHandler<T>) -> Unit): T =
    suspendCoroutine { k: Continuation<T> ->
        computation(k.asResultHandler())
    }


internal
fun <T> Continuation<T>.asResultHandler(): ResultHandler<T> =
    object : ResultHandler<T> {
        override fun onComplete(result: T) =
            resume(result)
        override fun onFailure(failure: GradleConnectionException) =
            resumeWithException(failure)
    }

