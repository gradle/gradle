import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.classycle
}

dependencies {
    compile(project(":core"))

    integTestCompile(project(":internalIntegTesting"))
    testFixturesApi(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":logging")
}
