tasks.register("run") {
    dependsOn(gradle.includedBuild("my-app").task(":app:run"))
}
