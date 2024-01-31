package com.example

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Restricted

class Abc {
    @Restricted
    var a: Int = 0
    
    @Restricted
    fun b(): Int = 1
    
    @Restricted
    var str: String = ""
    
    @Adding
    fun c(x: Int, configure: C.() -> Unit = { }) =
        C().apply {
            this.x = x;
            configure();
            cItems.add(this)
        }

    internal val cItems = mutableListOf<C>()
}

class C(var x: Int = 0) {
    @Builder
    fun d(newD: D): C {
        this.d = newD
        return this
    }
    
    @Restricted
    var d: D = D()
    
    @Restricted
    val y = "test"
    
    @Restricted
    fun f(y: String) = 0
}

class D {
    @Restricted
    var id: String = "none"
}

fun newD(id: String): D = D().also { it.id = id }
