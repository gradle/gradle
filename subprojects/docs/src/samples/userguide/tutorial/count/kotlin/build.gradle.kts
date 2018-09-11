task("count") {
    doLast {
        repeat(4) { print("$it ") }
    }
}
