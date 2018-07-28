// This script is automatically exposed to downstream consumers
// as the `my-plugin` org.gradle.api.Project plugin

tasks {
    register("myCopyTask", Copy::class.java) {
        group = "sample"
        from("build.gradle.kts")
        into("build/copy")
    }
}
