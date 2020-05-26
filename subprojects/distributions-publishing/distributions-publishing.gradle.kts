plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":distributionsMinimal"))

    runtimeOnly(project(":publish"))
    runtimeOnly(project(":signing"))
    runtimeOnly(project(":maven"))
    runtimeOnly(project(":ivy"))
    runtimeOnly(project(":pluginDevelopment"))
}
