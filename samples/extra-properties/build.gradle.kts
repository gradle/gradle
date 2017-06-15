val myTask = task("myTask") {

    val foo by extra { 42 }

    doLast {
        println("Extra property value: $foo")
    }
}

val foo: Int by myTask.extra
afterEvaluate {
    println("myTask.foo = $foo")
}

defaultTasks(myTask.name)
