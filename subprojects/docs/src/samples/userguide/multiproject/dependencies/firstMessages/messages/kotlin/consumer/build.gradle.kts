tasks.register("action") {
    doLast {
        println("Consuming message: ${rootProject.extra["producerMessage"]}")
    }
}
