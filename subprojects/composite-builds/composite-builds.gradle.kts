import org.gradle.gradlebuild.unittestandcompile.ModuleType

dependencies {
    compile(project(":core"))
    compile(project(":dependencyManagement"))
    compile(project(":launcher"))

    integTestRuntime(project(":toolingApiBuilders"))
    integTestRuntime(project(":ide"))
    integTestRuntime(project(":pluginDevelopment"))
    integTestRuntime(project(":testKit"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":dependencyManagement")
    from(":launcher")
}

testFilesCleanup {
    isErrorWhenNotEmpty = false
}
