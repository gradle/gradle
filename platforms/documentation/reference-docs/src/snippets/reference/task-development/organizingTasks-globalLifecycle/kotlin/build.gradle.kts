val globalBuildGroup = "My global build"
val ciBuildGroup = "My CI build"

// tag::global-lifecycle[]
tasks.register("qualityCheckApp") {
    group = globalBuildGroup
    description = "Runs checks on app (globally)"
    dependsOn(":app:qualityCheck" )
}

tasks.register("checkAll") {
    group = ciBuildGroup
    description = "Runs checks for all projects (CI)"
    dependsOn(subprojects.map { ":${it.name}:check" })
    dependsOn(gradle.includedBuilds.map { it.task(":checkAll") })
}
// end::global-lifecycle[]
