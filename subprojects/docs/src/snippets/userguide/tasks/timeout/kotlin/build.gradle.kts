import java.time.Duration

tasks {
    register("hangingTask") {
        doLast {
            Thread.sleep(100000)
        }
        timeout.set(Duration.ofMillis(500))
    }
}
