tasks.register<Copy>("copy")

task("copy", "overwrite" to true).apply {
    doLast {
        println("I am the new one.")
    }
}
