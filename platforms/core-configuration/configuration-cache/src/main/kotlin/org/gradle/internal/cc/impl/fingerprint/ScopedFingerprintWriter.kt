/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.withPropertyTrace
import java.io.Closeable
import java.util.ArrayDeque
import java.util.Queue


internal class ScopedFingerprintWriter<T>(
    private val writeContext: CloseableWriteContext
) : Closeable {
    private class PendingWrite<out T>(val value: T, val trace: PropertyTrace?)

    private val pendingWrites: Queue<PendingWrite<T>> = ArrayDeque()

    private var isWriting = false

    override fun close() {
        // we synchronize access to all resources used by callbacks
        // in case there was still an event being dispatched at closing time.
        synchronized(writeContext) {
            require(!isWriting && pendingWrites.isEmpty()) {
                "Incomplete write detected"
            }

            writeValue(null)
            writeContext.close()
        }
    }

    fun write(value: T, trace: PropertyTrace? = null) {
        synchronized(writeContext) {
            if (isWriting) {
                // This re-entrance can only happen on the same thread that does the enclosing write because of the synchronized block.

                // Writing a value may cause another value to be written, e.g. when `provider {}` used as a ValueSource parameter reads an environment variable.
                // These are valid configuration inputs, but we cannot write them immediately, or we will break the layout of the outer value.
                // For example, we might be writing value source parameters bean, and we get to serialize `provider { System.getenv(...) }`.
                // CC writes only the value of the provider, so it computes it. That triggers another call to this method for the environment variable input.
                // If we write the input synchronously, it will take the position intended for the provider's value, and the decoder won't be able to
                // reconstruct the provider.
                postponeWrite(value, trace)
                return
            }

            isWriting = true
            try {
                doWrite(value, trace)
                // Now write all inputs discovered while writing the first one or any of the consequent ones.
                drainPendingWrites()
            } finally {
                isWriting = false
            }
        }
    }

    private fun postponeWrite(value: T, trace: PropertyTrace?) {
        pendingWrites.add(PendingWrite(value, trace))
    }

    private fun drainPendingWrites() {
        while (!pendingWrites.isEmpty()) {
            val nextWrite = pendingWrites.remove()
            doWrite(nextWrite.value, nextWrite.trace)
        }
    }

    private fun doWrite(value: T, trace: PropertyTrace?) {
        withPropertyTrace(trace) {
            writeValue(value)
        }
    }

    private fun writeValue(value: T?) {
        writeContext.runWriteOperation {
            write(value)
        }
    }

    private inline fun withPropertyTrace(trace: PropertyTrace?, block: ScopedFingerprintWriter<T>.() -> Unit) {
        if (trace != null) {
            writeContext.withPropertyTrace(trace) { block() }
        } else {
            block()
        }
    }
}
