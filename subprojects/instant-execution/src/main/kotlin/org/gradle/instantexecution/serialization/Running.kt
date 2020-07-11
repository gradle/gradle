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
        is RecursiveContext<*, *> -> {
            val recursiveContext = this as RecursiveContext<Any?, Any?>
            val function: RecursiveFunction<Any?, Any?> = { value ->
                recursiveContext.applyFunctionTo(value)
            }
            val scope = DefaultRecursiveFunctionScope<Any?, Any?>(function, null)
            val previous = recursiveContext.recursionScope
            try {
                recursiveContext.recursionScope = scope
                val operation = recursiveOperation as Function2<T, Continuation<Any?>, Any?>
                return when (val result = operation.invoke(this, scope)) {
                    COROUTINE_SUSPENDED -> scope.runCallLoop() as R
                    else -> result as R
                }
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
interface RecursiveContext<T, R> {

    var recursionScope: RecursiveFunctionScope<T, R>?

    suspend fun applyFunctionTo(value: T): R
}


internal
sealed class RecursiveFunctionScope<T, R> {
    abstract suspend fun recur(value: T): R
}


private
typealias RecursiveFunction<T, R> = suspend (T) -> R


private
typealias UndecoratedRecursiveFunction = Function2<Any?, Continuation<Any?>?, Any?>


private
val UNDEFINED_RESULT = Result.success(COROUTINE_SUSPENDED)


private
class DefaultRecursiveFunctionScope<T, R>(
    recursiveFunction: RecursiveFunction<T, R>,
    value: T
) : RecursiveFunctionScope<T, R>(), Continuation<R> {

    private
    var function: UndecoratedRecursiveFunction = recursiveFunction as UndecoratedRecursiveFunction

    // Value to call function with
    private
    var value: Any? = value

    // Continuation of the current call
    private
    var k: Continuation<Any?>? = this as Continuation<Any?>

    // Completion result (completion of the whole call stack)
    private
    var result: Result<Any?> = UNDEFINED_RESULT

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<R>) {
        this.k = null
        this.result = result
    }

    override suspend fun recur(value: T): R = suspendCoroutineUninterceptedOrReturn { k ->
        this.k = k as Continuation<Any?>
        this.value = value
        COROUTINE_SUSPENDED
    }

    fun runCallLoop(): R {
        while (true) {
            val result = this.result
            val k = this.k // null means done
                ?: return (result as Result<R>).getOrThrow() // done -- final result
            // ~startCoroutineUninterceptedOrReturn
            val r = try {
                function(value, k)
            } catch (e: Throwable) {
                k.resumeWithException(e)
                continue
            }
            if (r !== COROUTINE_SUSPENDED)
                k.resume(r as R)
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
