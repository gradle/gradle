package com.example.com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.Adding

class MyClass {
    lateinit var my: MyClass
}

class TopLevel {
    fun my1(): MyClass = MyClass()
    fun my2(): MyClass = MyClass()
    
    @Adding
    fun my(configure: MyClass.() -> Unit) = MyClass().also(configure)
}