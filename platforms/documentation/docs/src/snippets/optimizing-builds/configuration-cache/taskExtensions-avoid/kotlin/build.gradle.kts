version = "1.0"

// tag::avoid[]
tasks.register("release") {
    extra["banner"] = "=== Releasing build ${rootProject.version} ==="
    doLast {
        // BAD: reading extra properties at execution time
        val banner = extra["banner"] as String
        println(banner)
    }
}
// end::avoid[]
