val myTask = task("myTask") {

    extra["foo"] = 42

    doLast {
        println("Extra property value: ${extra["foo"]}")
    }
}

afterEvaluate {
    println("myTask.foo = ${myTask.extra["foo"]}")
}

defaultTasks(myTask.name)
