package org.gradle.script.lang.kotlin.support.asm.fixture

@Suppress("unused_parameter")
class RemoveMethodsFixture {
    fun m1(i: Int) {}
    fun m2(i: Int, j: Long) {}
    fun m3(s: String) {}
}

@Suppress("unused_parameter")
class GenericRemoveMethodsFixture<in T> {
    fun m4(i: Int, t: T) {}
}
