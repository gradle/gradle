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
package org.gradle.instantexecution.serialization

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


/**
 * Runs the given [readOperation] synchronously.
 */
internal
fun <T : ReadContext, R> T.runReadOperation(readOperation: suspend T.() -> R): R {
    val callStack = saveCallStack()
    try {
        return runToCompletion {
            readOperation()
        }
    } finally {
        restoreCallStack(callStack)
    }
}


/**
 * Runs the given [writeOperation] synchronously.
 */
internal
fun <T : WriteContext> T.runWriteOperation(writeOperation: suspend T.() -> Unit) {
    val callStack = saveCallStack()
    try {
        runToCompletion {
            writeOperation()
        }
    } finally {
        restoreCallStack(callStack)
    }
}


/**
 * [Starts][startCoroutine] the suspending [block], asserts it runs
 * to completion and returns its result.
 */
@Suppress("unchecked_cast")
private
fun <R> runToCompletion(block: suspend () -> R): R {
    val blockFunction = block as Function1<Continuation<R>, Any?>
    val result = blockFunction.invoke(continuation)
    require(result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
        "Coroutine didn't run to completion."
    }
    return result as R
}


private
val continuation = Continuation<Any?>(EmptyCoroutineContext) {
    throw IllegalStateException("Illegal continuation: $it")
}
