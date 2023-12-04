defaultTasks("run")

// tag::run[]
tasks.register("run") {
    dependsOn(gradle.includedBuild("my-app").task(":app:run"))
}
// end::run[]

tasks.register("checkAll") {
    dependsOn(gradle.includedBuild("my-app").task(":app:check"))
    dependsOn(gradle.includedBuild("my-utils").task(":number-utils:check"))
    dependsOn(gradle.includedBuild("my-utils").task(":string-utils:check"))
}
