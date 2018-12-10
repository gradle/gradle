val hello = tasks.register("hello") {
    doLast {
        println("Hello Earth")
    }
}
// FIXME: Do the same as Groovy
hello.doFirst {
    println("Hello Venus")
}
hello.doLast {
    println("Hello Mars")
}
hello.apply {
    doLast {
        println("Hello Jupiter")
    }
}
