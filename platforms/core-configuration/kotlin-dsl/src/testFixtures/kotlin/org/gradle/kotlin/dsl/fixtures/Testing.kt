package org.gradle.kotlin.dsl.fixtures

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.kotlin.dsl.support.normaliseLineSeparators
import org.gradle.util.internal.TextUtil

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.fail

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

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


inline fun <reified T> withInstanceOf(o: Any, block: T.() -> Unit) {
    block(assertInstanceOf<T>(o))
}


inline fun <reified T> assertInstanceOf(o: Any): T {
    assertThat(o, instanceOf(T::class.java))
    return o as T
}


inline fun withClassLoaderFor(vararg classPath: File, action: ClassLoader.() -> Unit) =
    classLoaderFor(*classPath).use(action)


inline fun withClassLoaderFor(classPath: ClassPath, action: ClassLoader.() -> Unit) =
    classLoaderFor(classPath).use(action)


fun classLoaderFor(classPath: ClassPath): URLClassLoader =
    classLoaderFor(*classPath.asFiles.toTypedArray())


fun classLoaderFor(vararg classPath: File): URLClassLoader =
    URLClassLoader.newInstance(
        classPath.map { it.toURI().toURL() }.toTypedArray()
    )


val File.normalisedPath
    get() = TextUtil.normaliseFileSeparators(path)


fun assertStandardOutputOf(expected: String, action: () -> Unit): Unit =
    assertThat(
        standardOutputOf(action),
        equalTo(expected.normaliseLineSeparators())
    )


fun standardOutputOf(action: () -> Unit): String =
    ByteArrayOutputStream().also {
        val out = System.out
        try {
            System.setOut(PrintStream(it, true))
            action()
        } finally {
            System.setOut(out)
        }
    }.toString("utf8").normaliseLineSeparators()


fun clickableUrlFor(file: File): String =
    ConsoleRenderer().asClickableFileUrl(file)
