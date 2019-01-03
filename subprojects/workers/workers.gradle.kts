import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.classycle
}

dependencies {
    compile(project(":core"))
    compile(library("jcip"))

    integTestCompile(project(":internalIntegTesting"))
    testFixturesApi(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":logging")
}
