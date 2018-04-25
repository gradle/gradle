// This script is automatically exposed to downstream consumers
// as the `my-plugin` org.gradle.api.Project plugin

tasks {
    "myCopyTask"(Copy::class) {
        group = "sample"
        from("build.gradle.kts")
        into("build/copy")
    }
}
