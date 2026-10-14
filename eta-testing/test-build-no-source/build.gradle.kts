val intro = tasks.register("intro") {
    doLast {
        println("4 tasks have no sources, so they are NO-SOURCE on every run. 4 other tasks take 2s each, then a finish task takes 2s.")
        println("Shows tasks without sources recorded as taking no time, so they do not make the ETA unavailable.")
    }
}

(1..4).forEach { i ->
    tasks.register("noSource$i") {
        dependsOn(intro)
        // The sources directory does not exist
        inputs.files(fileTree("src/missing$i")).skipWhenEmpty()
        doLast { Thread.sleep(2000) }
    }
}
(1..4).forEach { i ->
    tasks.register("work$i") {
        dependsOn(intro)
        doLast { Thread.sleep(2000) }
    }
}
tasks.register("finish") {
    dependsOn(intro, (1..4).map { "noSource$it" }, (1..4).map { "work$it" })
    doLast { Thread.sleep(2000) }
}
tasks.register("all") { dependsOn(intro, "finish") }
