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

package org.gradle.internal.extensions.stdlib

import java.util.function.Supplier
import kotlin.reflect.KProperty

/**
 * Creates a property delegate for a thread local initialized with `initial`. For example, you can define
 * `val local by threadLocal { false }` and then use `local` as a boolean variable.
 *
 * @see [ThreadLocal.withInitial]
 */
fun <T> threadLocal(initial: Supplier<T>): ThreadLocalDelegate<T> = ThreadLocalDelegate.withInitial(initial)


/**
 * An adapter that allows using [ThreadLocal] as a property delegate, for example `var local by threadLocal { false }`.
 */
@JvmInline
value class ThreadLocalDelegate<T> private constructor(private val threadLocal: ThreadLocal<T>) {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = threadLocal.get()

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = threadLocal.set(value)

    companion object {
        /**
         * Wraps `ThreadLocal.withInitial(initial)` result with the delegate. Consider using [threadLocal] for brevity instead of this method.
         */
        internal
        fun <T> withInitial(initial: Supplier<T>): ThreadLocalDelegate<T> = ThreadLocalDelegate(ThreadLocal.withInitial(initial))
    }
}
