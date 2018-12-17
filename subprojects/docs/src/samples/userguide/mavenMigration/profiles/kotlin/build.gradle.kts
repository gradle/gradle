val buildProfile: String? by project  // <1>

apply(from = "profile-${buildProfile ?: "default"}.gradle.kts")  // <2>

val message: String by extra
tasks.register("greeting") {
    doLast {
        println(message)  // <3>
    }
}
