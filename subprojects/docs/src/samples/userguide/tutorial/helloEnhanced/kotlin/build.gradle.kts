val hello = task("hello") {
    doLast {
        println("Hello Earth")
    }
}
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
