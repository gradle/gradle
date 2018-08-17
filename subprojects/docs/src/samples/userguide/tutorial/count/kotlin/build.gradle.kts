task("count") {
    doLast {
        repeat(4) { println("$it ") }
    }
}
