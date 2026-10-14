// Counts the runs of this build, so each run can take longer than the last
fun runCountFile() = layout.buildDirectory.file("run-count.txt")

val intro = tasks.register("intro") {
    val countFile = runCountFile()
    doLast {
        val file = countFile.get().asFile
        val runCount = if (file.exists()) file.readText().trim().toInt() + 1 else 1
        file.parentFile.mkdirs()
        file.writeText(runCount.toString())
        println("8 parallel tasks get 2s slower on every run (this is run $runCount), so they always take longer than their recorded average. Then a finish task takes 3s.")
        println("Shows the ETA stalling, rather than counting up, while tasks overrun their estimates.")
    }
}

(1..8).forEach { i ->
    tasks.register("slower$i") {
        dependsOn(intro)
        val countFile = runCountFile()
        doLast {
            val runCount = countFile.get().asFile.readText().trim().toLong()
            Thread.sleep(2000 * runCount)
        }
    }
}
tasks.register("finish") {
    dependsOn(intro)
    dependsOn((1..8).map { "slower$it" })
    doLast { Thread.sleep(3000) }
}
tasks.register("all") { dependsOn(intro, "finish") }
