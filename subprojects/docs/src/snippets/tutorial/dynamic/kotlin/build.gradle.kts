repeat(4) { counter ->
    val taskName = "task" + counter
    tasks.register(taskName) {
        doLast {
            println("I'm task number: " + counter)
        }
    }
}
