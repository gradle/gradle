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
package org.gradle.internal.serialize.graph

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


/**
 * Runs the given [readOperation] synchronously.
 */
fun <T : ReadContext, R> T.runReadOperation(readOperation: suspend T.() -> R): R {
    contract {
        callsInPlace(readOperation, InvocationKind.EXACTLY_ONCE)
    }
    return runToCompletion {
        readOperation()
    }
}


/**
 * Runs the given [writeOperation] synchronously.
 */
fun <T : WriteContext, U> T.runWriteOperation(writeOperation: suspend T.() -> U): U =
    runToCompletion {
        writeOperation()
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
