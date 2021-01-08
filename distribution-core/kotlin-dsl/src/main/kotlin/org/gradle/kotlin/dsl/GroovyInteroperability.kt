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
import groovy.lang.GroovyObject
import groovy.lang.MetaClass
import org.codehaus.groovy.runtime.InvokerHelper.getMetaClass
import org.gradle.kotlin.dsl.support.uncheckedCast
import org.gradle.kotlin.dsl.support.unsafeLazy


/**
 * Adapts a Kotlin function to a single argument Groovy [Closure].
 *
 * @param T the expected type of the single argument to the closure.
 * @param action the function to be adapted.
 *
 * @see [KotlinClosure1]
 */
fun <T> Any.closureOf(action: T.() -> Unit): Closure<Any?> =
    KotlinClosure1(action, this, this)


/**
 * Adapts a Kotlin function to a Groovy [Closure] that operates on the
 * configured Closure delegate.
 *
 * @param T the expected type of the delegate argument to the closure.
 * @param action the function to be adapted.
 *
 * @see [KotlinClosure1]
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
 * @see [Closure]
 */
open class KotlinClosure0<V : Any>(
    val function: () -> V?,
    owner: Any? = null,
    thisObject: Any? = null
) : groovy.lang.Closure<V?>(owner, thisObject) {

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
 * @see [Closure]
 */
class KotlinClosure1<in T : Any?, V : Any>(
    val function: T.() -> V?,
    owner: Any? = null,
    thisObject: Any? = null
) : Closure<V?>(owner, thisObject) {

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
 * @see [Closure]
 */
class KotlinClosure2<in T : Any?, in U : Any?, V : Any>(
    val function: (T, U) -> V?,
    owner: Any? = null,
    thisObject: Any? = null
) : Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(t: T, u: U): V? = function(t, u)
}


/**
 * Adapts a ternary Kotlin function to a ternary Groovy [Closure].
 *
 * @param T the type of the first argument.
 * @param U the type of the second argument.
 * @param V the type of the third argument.
 * @param R the return type.
 * @param function the function to be adapted.
 * @param owner optional owner of the Closure.
 * @param thisObject optional _this Object_ of the Closure.
 *
 * @see [Closure]
 */
class KotlinClosure3<in T : Any?, in U : Any?, in V : Any?, R : Any>(
    val function: (T, U, V) -> R?,
    owner: Any? = null,
    thisObject: Any? = null
) : Closure<R?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(t: T, u: U, v: V): R? = function(t, u, v)
}


/**
 * Enables function invocation syntax on [Closure] references.
 */
operator fun <T> Closure<T>.invoke(): T = call()


/**
 * Enables function invocation syntax on [Closure] references.
 */
operator fun <T> Closure<T>.invoke(x: Any?): T = call(x)


/**
 * Enables function invocation syntax on [Closure] references.
 */
operator fun <T> Closure<T>.invoke(vararg xs: Any?): T = call(*xs)


/**
 * Executes the given [builder] against this object's [GroovyBuilderScope].
 *
 * @see [GroovyBuilderScope]
 */
inline fun <T> Any.withGroovyBuilder(builder: GroovyBuilderScope.() -> T): T =
    GroovyBuilderScope.of(this).builder()


/**
 * Provides a dynamic dispatching DSL with Groovy semantics for better integration with
 * plugins that rely on Groovy builders such as the core `maven` plugin.
 *
 * It supports Groovy keyword arguments and arbitrary nesting, for instance, the following Groovy code:
 *
 * ```Groovy
 * repository(url: "scp://repos.mycompany.com/releases") {
 *   authentication(userName: "me", password: "myPassword")
 * }
 * ```
 *
 * Can be mechanically translated to the following Kotlin with the aid of `withGroovyBuilder`:
 *
 * ```Kotlin
 * withGroovyBuilder {
 *   "repository"("url" to "scp://repos.mycompany.com/releases") {
 *     "authentication"("userName" to "me", "password" to "myPassword")
 *   }
 * }
 * ```
 *
 * @see [withGroovyBuilder]
 */
interface GroovyBuilderScope : GroovyObject {

    companion object {

        /**
         * Creates a [GroovyBuilderScope] for the given [value].
         */
        fun of(value: Any): GroovyBuilderScope =
            when (value) {
                is GroovyObject -> GroovyBuilderScopeForGroovyObject(value)
                else -> GroovyBuilderScopeForRegularObject(value)
            }
    }

    /**
     * The delegate of this [GroovyBuilderScope].
     */
    val delegate: Any

    /**
     * Invokes with Groovy semantics and [arguments].
     */
    operator fun String.invoke(vararg arguments: Any?): Any?

    /**
     * Invokes with Groovy semantics and no arguments.
     */
    operator fun String.invoke(): Any? =
        invoke(*emptyArray<Any>())

    /**
     * Invokes with Groovy semantics, [arguments] and provides a nested [GroovyBuilderScope].
     */
    operator fun <T> String.invoke(vararg arguments: Any?, builder: GroovyBuilderScope.() -> T): Any? =
        invoke(*arguments, closureFor(builder))

    /**
     * Invokes with Groovy semantics, no arguments, and provides a nested [GroovyBuilderScope].
     */
    operator fun <T> String.invoke(builder: GroovyBuilderScope.() -> T): Any? =
        invoke(closureFor(builder))

    /**
     * Invokes with Groovy semantics, named [keywordArguments], and provides a nested [GroovyBuilderScope].
     */
    operator fun <T> String.invoke(vararg keywordArguments: Pair<String, Any?>, builder: GroovyBuilderScope.() -> T): Any? =
        invoke(keywordArguments.toMap(), closureFor(builder))

    /**
     * Invokes with Groovy semantics and named [keywordArguments].
     */
    operator fun String.invoke(vararg keywordArguments: Pair<String, Any?>): Any? =
        invoke(keywordArguments.toMap())

    private
    fun <T> closureFor(builder: GroovyBuilderScope.() -> T): Closure<Any?> =
        object : Closure<Any?>(this, this) {
            @Suppress("unused")
            fun doCall() = delegate.withGroovyBuilder(builder)
        }
}


private
class GroovyBuilderScopeForGroovyObject(override val delegate: GroovyObject) : GroovyBuilderScope {

    override fun String.invoke(vararg arguments: Any?): Any? =
        delegate.invokeMethod(this, arguments)

    override fun getProperty(propertyName: String?): Any? {
        return delegate.getProperty(propertyName)
    }

    override fun setProperty(propertyName: String?, newValue: Any?) {
        return delegate.setProperty(propertyName, newValue)
    }

    override fun getMetaClass(): MetaClass {
        return delegate.metaClass
    }

    override fun setMetaClass(metaClass: MetaClass?) {
        delegate.metaClass = metaClass
    }

    override fun invokeMethod(name: String, args: Any?): Any? =
        delegate.invokeMethod(name, args)
}


private
class GroovyBuilderScopeForRegularObject(override val delegate: Any) : GroovyBuilderScope {

    private
    val groovyMetaClass: MetaClass by unsafeLazy {
        getMetaClass(delegate)
    }

    override fun invokeMethod(name: String, args: Any?): Any? =
        groovyMetaClass.invokeMethod(delegate, name, args)

    override fun setProperty(propertyName: String, newValue: Any?) =
        groovyMetaClass.setProperty(delegate, propertyName, newValue)

    override fun getProperty(propertyName: String): Any =
        groovyMetaClass.getProperty(delegate, propertyName)

    override fun setMetaClass(metaClass: MetaClass?) =
        throw IllegalStateException()

    override fun getMetaClass(): MetaClass =
        groovyMetaClass

    override fun String.invoke(vararg arguments: Any?): Any? =
        groovyMetaClass.invokeMethod(delegate, this, arguments)
}
