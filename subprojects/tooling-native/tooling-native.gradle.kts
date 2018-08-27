
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

testFixtures {
    from(":platformNative")
}
