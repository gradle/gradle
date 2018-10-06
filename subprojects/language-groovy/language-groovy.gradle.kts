import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":languageJava"))

    implementation(library("asm"))

    // TODO - get rid of this cycle
    integTestRuntime(project(":plugins"))
}

gradlebuildJava {
    // Needs to run in the compiler daemon
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":languageJvm", "integTest")
    from(":languageJvm", "testFixtures")
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/internal/tasks/compile/**",
        "org/gradle/api/tasks/javadoc/**"
    ))
}
