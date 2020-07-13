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
@file:Suppress("unchecked_cast")

package org.gradle.instantexecution.serialization

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine


/**
 * Runs the given [readOperation] synchronously.
 */
internal
fun <T : ReadContext, R> T.runReadOperation(readOperation: suspend T.() -> R): R =
    runRecursiveOperation(readOperation)


/**
 * Runs the given [writeOperation] synchronously.
 */
internal
fun <T : WriteContext, R> T.runWriteOperation(writeOperation: suspend T.() -> R) =
    runRecursiveOperation(writeOperation)


/**
 * Runs the [recursiveOperation] on the current context [T].
 *
 * Implementation heavily inspired by [Deep recursion with coroutines](https://medium.com/@elizarov/deep-recursion-with-coroutines-7c53e15993e3).
 */
private
fun <T, R> T.runRecursiveOperation(recursiveOperation: suspend T.() -> R): R {
    when (this) {
        is RecursiveContext -> {
            val recursiveContext = this as RecursiveContext
            val function: RecursiveFunction = { value ->
                recursiveContext.applyFunctionTo(value)
            }
            val scope = DefaultRecursionScope(function, null)
            val previous = recursiveContext.recursionScope
            try {
                recursiveContext.recursionScope = scope
                val operation = recursiveOperation as Function2<T, Continuation<Any?>, Any?>
                val result = operation.invoke(this, scope)
                if (result !== COROUTINE_SUSPENDED) {
                    return result as R
                }
                return scope.runCallLoop() as R
            } finally {
                recursiveContext.recursionScope = previous
            }
        }
        else -> {
            // support for custom context implementations used by tests
            return runToCompletion {
                recursiveOperation()
            }
        }
    }
}


internal
interface RecursiveContext {

    var recursionScope: RecursionScope?

    suspend fun applyFunctionTo(value: Any?): Any?
}


internal
sealed class RecursionScope {
    abstract suspend fun recur(value: Any?): Any?
}


private
typealias RecursiveFunction = suspend (Any?) -> Any?


private
typealias UndecoratedRecursiveFunction = Function2<Any?, Continuation<Any?>?, Any?>


private
val UNDEFINED_RESULT = Result.success(COROUTINE_SUSPENDED)


private
class DefaultRecursionScope(
    recursiveFunction: RecursiveFunction,
    value: Any?
) : RecursionScope(), Continuation<Any?> {

    private
    var function: UndecoratedRecursiveFunction = recursiveFunction as UndecoratedRecursiveFunction

    // Value to call function with
    private
    var value: Any? = value

    // Continuation of the current call
    private
    var k: Continuation<Any?>? = this

    // Completion result (completion of the whole call stack)
    private
    var result: Result<Any?> = UNDEFINED_RESULT

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any?>) {
        this.k = null
        this.result = result
    }

    override suspend fun recur(value: Any?): Any? = suspendCoroutineUninterceptedOrReturn { k ->
        this.k = k
        this.value = value
        COROUTINE_SUSPENDED
    }

    fun runCallLoop(): Any? {
        var iteration = 0
        while (true) {
            val k = this.k
            val result = this.result
            if (k == null) {
                return result.getOrThrow()  // done -- final result
            }
            iteration += 1
            if (iteration > 1_000_000) {
                throw IllegalStateException("Too many iterations! Are we stuck? {value: $value, k: $k, result: $result}")
            }
            // ~startCoroutineUninterceptedOrReturn
            val r = try {
                function(value, k)
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Throwable) {
                k.resumeWithException(e)
                continue
            }
            if (r !== COROUTINE_SUSPENDED)
                k.resume(r)
        }
    }
}


/**
 * [Starts][startCoroutine] the suspending [block], asserts it runs
 * to completion and returns its result.
 */
private
fun <R> runToCompletion(block: suspend () -> R): R {
    var completion: Result<R>? = null
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) {
            completion = it
        }
    )
    return completion.let {
        require(it != null) {
            "Coroutine didn't run to completion."
        }
        it.getOrThrow()
    }
}
