ant.importBuild("build.xml")

tasks {
    named<Task>("compile") {
        setDependsOn(listOf(":util:build"))
    }
}
