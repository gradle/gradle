import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(project(":testingJvm"))
    compile(project(":launcher"))
    compile(project(":toolingApi"))
    compile(project(":compositeBuilds"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

strictCompile {
    ignoreDeprecations = true
}
