import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
}

dependencies {
    compile(library("groovy"))
    compile(project(":core"))
    compile(project(":dependencyManagement"))
    compile(project(":workers"))
    compile(library("commons_collections"))
    compile(library("commons_lang"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
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
