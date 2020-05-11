plugins {
    idea
}

defaultTasks("run")

// tag::run[]
tasks.register("run") {
    dependsOn(gradle.includedBuild("my-app").task(":run"))
}
// end::run[]

tasks.register("checkAll") {
    dependsOn(gradle.includedBuild("my-app").task(":check"))
    dependsOn(gradle.includedBuild("my-utils").task(":number-utils:check"))
    dependsOn(gradle.includedBuild("my-utils").task(":string-utils:check"))
}
