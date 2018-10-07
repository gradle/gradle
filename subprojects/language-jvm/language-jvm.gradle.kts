import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":platformBase"))

    testRuntime(project(":languageJava"))

    testFixturesApi(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":launcher", "testFixtures")
}
