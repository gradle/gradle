println("configuring $project")
tasks.register("hello") {
    doLast {
        println("hello from other script")
    }
}
