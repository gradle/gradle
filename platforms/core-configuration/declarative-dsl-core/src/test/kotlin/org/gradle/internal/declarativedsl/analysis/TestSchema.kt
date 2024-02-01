package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.Adding
import org.gradle.internal.declarativedsl.Restricted

class MyClass {
    @Restricted
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
