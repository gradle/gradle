includeBuild("core") {
    dependencySubstitution {
        substitute(module("composite-builds:core")).with(project(":"))
    }
}
includeBuild("cli")
