val myTask = task("myTask") {

    val foo by extra { 42 }
    val bar by extra<Int?>(null)
    val bazar: Int? by extra { null }

    doLast {
        println("Extra foo property value: $foo")
        println("Optional extra bar property value: $bar")
        println("Optional extra bazar property value: $bazar")
    }
}

val foo: Int by myTask.extra
val bar: Int? by myTask.extra
val bazar: Int by myTask.extra

afterEvaluate {
    println("myTask.foo = $foo")
    println("myTask.bar = $bar")
    try {
        println("myTask.bazar = $bazar")
        require(false, { "Should not happen as `bazar`, requested as a Int is effectively null" })
    } catch (ex: NullPointerException) {
        // expected
    }
}

defaultTasks(myTask.name)
