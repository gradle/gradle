package com.example

fun Abc.body() {
    val myB = b()

    a = myB
    val myD = newD("shared")

    c(1) {
        x = f(y)
        d = myD
    }
    c(2) {
        x = f("another test")
        d = myD
    }
}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val receiver = Abc()
        receiver.body()
        println(receiver.cItems.map { "C(d = D(id = ${it.d.id}), x = ${it.x})" })
    }
}