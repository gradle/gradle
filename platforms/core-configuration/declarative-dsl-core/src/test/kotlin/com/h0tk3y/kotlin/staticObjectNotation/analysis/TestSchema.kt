package com.example.com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Restricted

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