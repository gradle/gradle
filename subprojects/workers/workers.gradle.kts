
plugins {
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(library("jcip"))

    integTestCompile(project(":internalIntegTesting"))
}

testFixtures {
    from(":core")
    from(":logging")
}
