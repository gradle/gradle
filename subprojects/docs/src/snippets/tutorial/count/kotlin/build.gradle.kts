tasks.register("count") {
    doLast {
        repeat(4) { print("$it ") }
    }
}
