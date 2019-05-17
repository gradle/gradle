import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":platformBase"))

    testRuntime(project(":languageJava"))

    testFixturesApi(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":launcher", "testFixtures")
}
