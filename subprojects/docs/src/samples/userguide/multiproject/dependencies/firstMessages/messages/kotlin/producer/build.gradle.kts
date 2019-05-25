tasks.register("action") {
    doLast {
        println("Producing message:")
        rootProject.extra["producerMessage"] = "Watch the order of execution."
    }
}
