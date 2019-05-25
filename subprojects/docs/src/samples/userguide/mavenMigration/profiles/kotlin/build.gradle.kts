val buildProfile: String? by project  // <1>

apply(from = "profile-${buildProfile ?: "default"}.gradle.kts")  // <2>

tasks.register("greeting") {
    val message: String by project.extra
    doLast {
        println(message)  // <3>
    }
}
