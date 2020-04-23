plugins {
    base
    id("com.example.my-plugin")
}

tasks.named("check") {
    dependsOn(gradle.includedBuilds.map { it.task(":check") })
}
