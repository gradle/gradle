tasks.create("compile") {
    doLast {
        println("compiling source")
    }
}
tasks.create("testCompile") {
    dependsOn("compile")
    doLast {
        println("compiling test source")
    }
}
tasks.create("test") {
    dependsOn("compile", "testCompile")
    doLast {
        println("running unit tests")
    }
}
tasks.create("build") {
    dependsOn("test")
}
