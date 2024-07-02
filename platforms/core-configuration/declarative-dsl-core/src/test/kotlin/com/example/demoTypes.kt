@file:Suppress("UNUSED_PARAMETER")

package com.example

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Builder
import org.gradle.declarative.dsl.model.annotations.Restricted


class Abc {
    @get:Restricted
    var a: Int = 0

    @Restricted
    @Suppress("FunctionOnlyReturningConstant")
    fun b(): Int = 1

    @get:Restricted
    var str: String = ""

    @Adding
    fun c(x: Int, configure: C.() -> Unit = { }) =
        C().apply {
            this.x = x
            configure()
            cItems.add(this)
        }

    internal
    val cItems = mutableListOf<C>()
}


class C(@get:Restricted var x: Int = 0) {
    @Builder
    fun d(newD: D): C {
        this.d = newD
        return this
    }

    @get:Restricted
    var d: D = D()

    @get:Restricted
    val y = "test"

    @Restricted
    @Suppress("FunctionOnlyReturningConstant")
    fun f(y: String) = 0
}


class D {
    @get:Restricted
    var id: String = "none"
}


fun newD(id: String): D = D().also { it.id = id }
