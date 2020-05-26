plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":distributionsJvm")) {
        because("the project dependency 'toolingNative -> ide' currently links this to the JVM ecosystem")
    }
    runtimeOnly(project(":distributionsPublishing")) {
        because("configuring publishing is part of the 'language native' support")
    }

    runtimeOnly(project(":languageNative"))
    runtimeOnly(project(":toolingNative"))
    runtimeOnly(project(":ideNative"))
    runtimeOnly(project(":testingNative"))
}
