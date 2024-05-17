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

package org.gradle.configurationcache.fingerprint

import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withPropertyTrace
import java.io.Closeable


internal
class ScopedFingerprintWriter<T>(
    private val writeContext: DefaultWriteContext
) : Closeable {
    override fun close() {
        // we synchronize access to all resources used by callbacks
        // in case there was still an event being dispatched at closing time.
        synchronized(writeContext) {
            unsafeWrite(null)
            writeContext.close()
        }
    }

    fun write(value: T, trace: PropertyTrace? = null) {
        synchronized(writeContext) {
            withPropertyTrace(trace) {
                unsafeWrite(value)
            }
        }
    }

    private
    fun unsafeWrite(value: T?) {
        writeContext.runWriteOperation {
            write(value)
        }
    }

    private
    inline fun withPropertyTrace(trace: PropertyTrace?, block: ScopedFingerprintWriter<T>.() -> Unit) {
        if (trace != null) {
            writeContext.withPropertyTrace(trace) { block() }
        } else {
            block()
        }
    }
}
