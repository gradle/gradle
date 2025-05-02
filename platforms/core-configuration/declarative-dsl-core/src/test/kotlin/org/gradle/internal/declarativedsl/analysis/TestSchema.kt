package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted


class MyClass {
    @get:Restricted
    lateinit var my: MyClass
}


class TopLevel {
    @Adding
    fun my1(): MyClass = MyClass()

    @Adding
    fun my2(): MyClass = MyClass()

    @Adding
    fun my(configure: MyClass.() -> Unit) = MyClass().also(configure)
}
