tasks.register("ok")

tasks.register("broken") {
    dependsOn("ok")
    doLast {
        throw RuntimeException("broken")
    }
}

gradle.taskGraph.beforeTask {
    println("executing $this ...")
}

gradle.taskGraph.afterTask {
    if (state.failure != null) {
        println("FAILED")
    } else {
        println("done")
    }
}
