task("action") {
    doLast {
        println("Consuming message: ${rootProject.extra["producerMessage"]}")
    }
}
