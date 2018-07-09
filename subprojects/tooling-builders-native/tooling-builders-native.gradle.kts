import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":languageNative"))
    compile(project(":testingNative"))
    compile(project(":toolingApi"))
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}
