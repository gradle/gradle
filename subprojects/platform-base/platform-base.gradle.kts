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
    // TODO Is the below syntaxt Kotlin best practices?
    testFixturesCompile(project(mapOf("path" to ":modelCore", "configuration" to "testFixturesUsageRuntime")))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":diagnostics", "testFixtures")
}

// TODO Why is this commented out and what needs to happen here?
// classycle {
//     excludePatterns = ["org.gradle.language.base.internal/**"]
// }
