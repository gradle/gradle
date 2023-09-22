internal object Example {
    fun sum(a: Int, b: Int) {
        val c = a + b
        println(c)
    }
}

tasks.register("count") {
    doLast {
        repeat(4) { print(Example.sum(it,it)) }
    }
}
