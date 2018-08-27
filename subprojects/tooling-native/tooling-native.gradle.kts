import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":languageNative"))
    compile(project(":testingNative"))
    compile(project(":toolingApi"))
    // To pick up various builders (which should live somewhere else)
    compile(project(":ide"))
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}

testFixtures {
    from(":platformNative")
}
