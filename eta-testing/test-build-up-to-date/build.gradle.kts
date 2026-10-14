val intro = tasks.register("intro") {
    doLast {
        println("6 tasks with inputs and outputs take 3s when they execute, and are up-to-date when run again with the same -Pinput. Then a finish task always takes 2s.")
        println("Shows up-to-date tasks estimated at their last execution time, as only executions are recorded, and the ETA dropping as soon as they complete.")
    }
}

(1..6).forEach { i ->
    tasks.register("cacheable$i") {
        dependsOn(intro)
        val input = providers.gradleProperty("input").orElse("default")
        val output = layout.buildDirectory.file("out/$i.txt")
        inputs.property("input", input)
        outputs.file(output)
        doLast {
            Thread.sleep(3000)
            output.get().asFile.writeText(input.get())
        }
    }
}
tasks.register("finish") {
    dependsOn(intro, (1..6).map { "cacheable$it" })
    doLast { Thread.sleep(2000) }
}
tasks.register("all") { dependsOn(intro, "finish") }
