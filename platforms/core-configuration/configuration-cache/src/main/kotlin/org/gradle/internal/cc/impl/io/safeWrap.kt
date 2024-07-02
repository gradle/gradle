/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.io

import java.io.Closeable


/**
 * Wraps an inner closeable into an outer closeable, while ensuring that
 * if the wrapper function fails, the inner closeable is closed before
 * the exception is thrown.
 *
 * @param innerSupplier the supplier that produces the inner closeable that is to be safely wrapped
 * @param unsafeWrapper a wrapping function that is potentially unsafe
 * @return the result of the wrapper function
 */
internal
fun <C : Closeable, T : Closeable> safeWrap(innerSupplier: () -> C, unsafeWrapper: (C) -> T): T {
    // fine if we fail here
    val innerCloseable = innerSupplier()
    val outerCloseable = try {
        // but if we fail here, we need to ensure we close
        // the inner closeable, or else it will leak
        unsafeWrapper(innerCloseable)
    } catch (e: Throwable) {
        try {
            innerCloseable.close()
        } catch (closingException: Throwable) {
            e.addSuppressed(closingException)
        }
        throw e
    }
    return outerCloseable
}
