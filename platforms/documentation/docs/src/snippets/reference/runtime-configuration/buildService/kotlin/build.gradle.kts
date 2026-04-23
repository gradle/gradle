import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.build.event.BuildEventsListenerRegistry

// tag::listener[]
abstract class BuildDurationService
    : BuildService<BuildServiceParameters.None>, AutoCloseable {

    private val startTime = System.currentTimeMillis()

    // Called once at the end of the build — reliable teardown hook
    override fun close() {
        val elapsed = System.currentTimeMillis() - startTime
        println("─────────────────────────────────────")
        println("  Build duration : ${elapsed}ms")
        println("─────────────────────────────────────")
    }
}

val buildDurationService = gradle.sharedServices.registerIfAbsent("buildDuration", BuildDurationService::class) {
    maxParallelUsages = 1
}

tasks.register("taskA") {
    group = "demo"
    usesService(buildDurationService)
    val service = buildDurationService
    doLast {
        service.get()
        println("taskA running...")
        Thread.sleep(200)
    }
}

tasks.register("taskB") {
    group = "demo"
    doLast {
        println("taskB running...")
        Thread.sleep(300)
    }
}
// end::listener[]

tasks.register("runAll") {
    group = "demo"
    description = "Runs all demo tasks."
    dependsOn("taskA", "taskB")
}
