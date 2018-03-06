package org.gradle.kotlin.dsl.fixtures

import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.fail

import java.io.File

import java.net.URLClassLoader

import kotlin.reflect.KClass


fun <T : Throwable> assertFailsWith(exception: KClass<out T>, block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        assertThat(e, instanceOf(exception.java))
        @Suppress("unchecked_cast")
        return e as T
    }
    fail("Expecting exception of type `$exception`, got none.")
    throw IllegalStateException()
}


inline
fun <reified T> withInstanceOf(o: Any, block: T.() -> Unit) {
    block(assertInstanceOf<T>(o))
}


inline
fun <reified T> assertInstanceOf(o: Any): T {
    assertThat(o, instanceOf(T::class.java))
    return o as T
}


fun classLoaderFor(vararg classPath: File): URLClassLoader =
    URLClassLoader.newInstance(
        classPath.map { it.toURI().toURL() }.toTypedArray())
