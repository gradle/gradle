import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":platformBase"))
    compile(project(":core"))
    compile(project(":diagnostics"))

    implementation(library("asm"))
    implementation(library("commons_io"))

    // To pick up JavaToolChainInternal implementation
    // TODO - get rid of cycle
    integTestRuntime(project(":languageJava"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":diagnostics")
    from(":platformBase")
}
