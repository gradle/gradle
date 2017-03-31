package org.gradle.script.lang.kotlin

import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.fail

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
