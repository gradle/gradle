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

import org.gradle.instantexecution.InstantExecutionThrowable
import java.io.IOException


/**
 * Rethrows the error if it [must bubble up][mustBubbleUp] to avoid multiple reports
 * of the same error or undue exception wrapping.
 */
@Suppress("nothing_to_inline")
internal
inline fun Throwable.bubbleUp() {
    if (mustBubbleUp) {
        throw this
    }
}


/**
 * [JVM errors][Error], [configuration cache errors][InstantExecutionThrowable], [IO exceptions][IOException] and
 * [interruptions][InterruptedException] must always bubble up.
 *
 * - [JVM errors][Error] because it doesn't make sense to try to recover from or try to report OOM/stack overflow style errors
 * during serialization.
 * - [Configuration cache errors][InstantExecutionThrowable] so they are reported only once.
 * - [IO exceptions][IOException] because codecs have no business with them.
 * - [Interruptions][InterruptedException] because the callstack might be holding locks.
 */
internal
val Throwable.mustBubbleUp: Boolean
    get() = when (this) {
        is Error -> true
        is InstantExecutionThrowable -> true
        is IOException -> true
        is InterruptedException -> true
        else -> false
    }
