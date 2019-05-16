import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`strict-compile`
}

dependencies {
    compile(library("groovy"))
    compile(project(":core"))
    compile(project(":dependencyManagement"))
    compile(project(":workers"))
    compile(project(":execution"))
    compile(library("commons_lang"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":coreApi")
    from(":core", "testFixtures")
    from(":modelCore", "testFixtures")
    from(":diagnostics", "testFixtures")
}

// classycle {
//     excludePatterns.set(listOf("org.gradle.language.base.internal/**"))
// }
