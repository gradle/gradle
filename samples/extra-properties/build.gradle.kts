val myTask = task("myTask") {

    var foo: Int by extra
    foo = 42

    doLast {
        println("Extra property value: $foo")
    }
}

val foo: Int by myTask.extra
afterEvaluate {
    println("myTask.foo = $foo")
}

defaultTasks(myTask.name)
