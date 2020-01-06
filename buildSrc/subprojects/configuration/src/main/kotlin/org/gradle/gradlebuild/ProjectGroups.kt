package org.gradle.gradlebuild

import org.gradle.api.Project


object ProjectGroups {
    val excludedFromVulnerabilityCheck = setOf(
        ":buildScanPerformance",
        ":distributions",
        ":docs",
        ":integTest",
        ":internalAndroidPerformanceTesting",
        ":internalIntegTesting",
        ":internalPerformanceTesting",
        ":internalTesting",
        ":performance",
        ":runtimeApiInfo",
        ":smokeTest",
        ":soak")

    // TODO Why is smoke test not on that list
    private
    val Project.internalProjects
        get() = rootProject.subprojects.filter {
            it.name.startsWith("internal")
                || it.name in internalProjectNames
                || it.name in kotlinJsProjectNames
        }.toSet()

    private
    val internalProjectNames = setOf(
        "integTest", "distributions", "performance", "buildScanPerformance",
        "kotlinCompilerEmbeddable", "kotlinDslTestFixtures", "kotlinDslIntegTests"
    )

    private
    val kotlinJsProjectNames = setOf(
        "instantExecutionReport"
    )

    val Project.kotlinJsProjects
        get() = kotlinJsProjectNames.map { project(":$it") }

    val Project.javaProjects
        get() = rootProject.subprojects - kotlinJsProjects - listOf(project(":distributionsDependencies"))

    val Project.publicJavaProjects
        get() = javaProjects - internalProjects

    val Project.pluginProjects
        get() = setOf("antlr", "plugins", "codeQuality", "wrapper", "maven",
            "ide", "scala", "signing", "ear", "javascript",
            "diagnostics", "reporting", "publish", "ivy", "jacoco", "buildInit", "platformBase",
            "platformJvm", "languageJvm", "languageJava", "languageGroovy", "languageScala",
            "platformNative", "platformPlay", "idePlay", "languageNative", "toolingNative", "ideNative",
            "testingBase", "testingNative", "testingJvm", "testingJunitPlatform", "pluginDevelopment", "pluginUse",
            "resourcesHttp", "resourcesSftp", "resourcesS3", "resourcesGcs", "compositeBuilds", "buildCacheHttp").map { rootProject.project(it) }.toSet()

    val Project.implementationPluginProjects
        get() = setOf(
            rootProject.project("buildProfile"),
            rootProject.project("toolingApiBuilders"),
            rootProject.project("kotlinDslProviderPlugins"),
            rootProject.project("kotlinDslToolingBuilders"),
            rootProject.project("instantExecution"),
            rootProject.project("security")
        )

    val Project.publicProjects
        get() = pluginProjects +
            implementationPluginProjects +
            publicJavaProjects +
            rootProject.project(":kotlinDsl") -
            setOf(":smokeTest", ":soak").map { rootProject.project(it) }
}
