val myTask = task("myTask") {

    val foo by extra { 42 }
    val bar by extra<Int?>(null)

    doLast {
        println("Extra foo property value: $foo")
        println("Optional extra bar property value: $bar")
    }
}

val foo: Int by myTask.extra
val bar: Int? by myTask.extra

afterEvaluate {
    println("myTask.foo = $foo")
    println("myTask.bar = $bar")
}

defaultTasks(myTask.name)
