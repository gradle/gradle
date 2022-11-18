val buildProfile: String? by project  // <1>

apply(from = "profile-${buildProfile ?: "default"}.gradle.kts")  // <2>

tasks.register("greeting") {
    // Store the message into a variable, because referencing extras from the task action
    // is not compatible with the configuration cache.
    val message = project.extra["message"]
    doLast {
        println(message)  // <3>
    }
}
