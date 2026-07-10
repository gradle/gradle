tasks {
    compileJava {
        dependsOn("prepare")  // <1>
    }
    named("package") {
        setDependsOn(listOf(compileJava))  // <2>
    }
    assemble {
        setDependsOn(listOf("package"))  // <3>
    }
}
