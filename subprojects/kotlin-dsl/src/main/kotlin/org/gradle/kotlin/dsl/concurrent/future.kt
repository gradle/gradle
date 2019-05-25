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

import com.google.common.annotations.VisibleForTesting

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


/**
 * Starts and exposes the given suspending [computation] as a [Future] value.
 *
 * The [computation] executes synchronously until its first suspension point.
 */
@VisibleForTesting
fun <T> future(context: CoroutineContext = EmptyCoroutineContext, computation: suspend () -> T): Future<T> =
    FutureContinuation<T>(context).also { k ->
        computation.startCoroutine(completion = k)
    }


private
class FutureContinuation<T>(override val context: CoroutineContext) : Future<T>, Continuation<T> {

    private
    var result: Result<T>? = null

    private
    val outcomeLatch = CountDownLatch(1)

    override fun resumeWith(result: Result<T>) {
        this.result = result
        outcomeLatch.countDown()
    }

    override fun isCancelled(): Boolean = false

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isDone(): Boolean = result != null

    override fun get(): T {
        outcomeLatch.await()
        return getOrThrow()
    }

    override fun get(timeout: Long, unit: TimeUnit): T =
        if (outcomeLatch.await(timeout, unit)) getOrThrow()
        else throw TimeoutException()

    private
    fun getOrThrow() = (result as Result<T>).getOrThrow()
}
