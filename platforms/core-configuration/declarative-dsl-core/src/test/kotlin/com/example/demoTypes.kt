@file:Suppress("UNUSED_PARAMETER")

package com.example

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HasDefaultValue


class Abc {
    var a: Int = 0

    @Suppress("FunctionOnlyReturningConstant")
    fun b(): Int = 1

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

    fun newD(id: String): D = D().also { it.id = id }
}


class C(var x: Int = 0) {
    @HasDefaultValue(false)
    fun d(newD: D): C {
        this.d = newD
        return this
    }

    @get:HasDefaultValue(false)
    var d: D = D()

    val y = "test"

    @Suppress("FunctionOnlyReturningConstant")
    fun f(y: String) = 0
}


class D {
    var id: String = "none"
}


