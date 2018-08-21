repeat(4) { counter ->
    task("task$counter") {
        doLast {
            println("I'm task number $counter")
        }
    }
}
tasks["task0"].dependsOn("task2", "task3")
