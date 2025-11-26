/*
 * Copyright 2025 the original author or authors.
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

import io.usethesource.capsule.Set
import java.util.concurrent.atomic.AtomicReference


internal
typealias AtomicSet<T> = AtomicReference<Set.Immutable<T>>


internal
fun <T> newAtomicSet(): AtomicSet<T> =
    AtomicSet(Set.Immutable.of())


internal
fun <T> AtomicSet<T>.remove(key: T) {
    updateAndGet {
        it.__remove(key)
    }
}


internal
fun <T> AtomicSet<T>.clear() {
    set(Set.Immutable.of())
}


internal
fun <T> AtomicSet<T>.addAll(keys: Collection<T>) {
    if (keys.isNotEmpty()) {
        updateAndGet {
            it.asTransient().apply {
                for (key in keys) {
                    __insert(key)
                }
            }.freeze()
        }
    }
}


internal
fun <T> AtomicSet<T>.add(key: T): Boolean {
    var added = false
    updateAndGet { prev ->
        prev.__insert(key).also { next ->
            added = next !== prev
        }
    }
    return added
}
