/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl

import groovy.lang.Closure
import org.gradle.internal.Cast.uncheckedCast


/**
 * Adapts a Kotlin function to a single argument Groovy [Closure].
 *
 * @param T the expected type of the single argument to the closure.
 * @param action the function to be adapted.
 *
 * @see KotlinClosure1
 */
fun <T : Any> Any.closureOf(action: T.() -> Unit): Closure<Any?> =
    KotlinClosure1(action, this, this)


/**
 * Adapts a Kotlin function to a Groovy [Closure] that operates on the
 * configured Closure delegate.
 *
 * @param T the expected type of the delegate argument to the closure.
 * @param action the function to be adapted.
 *
 * @see KotlinClosure1
 */
fun <T> Any.delegateClosureOf(action: T.() -> Unit) =
    object : Closure<Unit>(this, this) {
        @Suppress("unused") // to be called dynamically by Groovy
        fun doCall() = uncheckedCast<T>(delegate).action()
    }


/**
 * Adapts a parameterless Kotlin function to a parameterless Groovy [Closure].
 *
 * @param V the return type.
 * @param function the function to be adapted.
 * @param owner optional owner of the Closure.
 * @param thisObject optional _this Object_ of the Closure.
 *
 * @see Closure
 */
open class KotlinClosure0<V : Any>(
    val function : () -> V?,
    owner: Any? = null,
    thisObject: Any? = null) : groovy.lang.Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(): V? = function()
}


/**
 * Adapts an unary Kotlin function to an unary Groovy [Closure].
 *
 * @param T the type of the single argument to the closure.
 * @param V the return type.
 * @param function the function to be adapted.
 * @param owner optional owner of the Closure.
 * @param thisObject optional _this Object_ of the Closure.
 *
 * @see Closure
 */
class KotlinClosure1<in T : Any, V : Any>(
    val function: T.() -> V?,
    owner: Any? = null,
    thisObject: Any? = null) : Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(it: T): V? = it.function()
}


/**
 * Adapts a binary Kotlin function to a binary Groovy [Closure].
 *
 * @param T the type of the first argument.
 * @param U the type of the second argument.
 * @param V the return type.
 * @param function the function to be adapted.
 * @param owner optional owner of the Closure.
 * @param thisObject optional _this Object_ of the Closure.
 *
 * @see Closure
 */
class KotlinClosure2<in T : Any, in U : Any, V : Any>(
    val function: (T, U) -> V?,
    owner: Any? = null,
    thisObject: Any? = null) : Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(t: T, u: U): V? = function(t, u)
}


operator fun <T> Closure<T>.invoke(): T = call()

operator fun <T> Closure<T>.invoke(x: Any?): T = call(x)

operator fun <T> Closure<T>.invoke(vararg xs: Any?): T = call(*xs)
