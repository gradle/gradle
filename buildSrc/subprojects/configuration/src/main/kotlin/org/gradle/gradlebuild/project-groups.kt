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
        get() = rootProject.subprojects.filter { it.name.startsWith("internal") ||
            it.name in setOf("integTest", "distributions", "performance", "buildScanPerformance") }.toSet()

    val Project.javaProjects
        get() = rootProject.subprojects - rootProject.project("distributionsDependencies")

    val Project.publicJavaProjects
        get() = javaProjects - internalProjects


    val Project.pluginProjects
        get() = setOf("announce", "antlr", "plugins", "codeQuality", "wrapper", "osgi", "maven",
            "ide", "scala", "signing", "ear", "javascript", "buildComparison",
            "diagnostics", "reporting", "publish", "ivy", "jacoco", "buildInit", "platformBase",
            "platformJvm", "languageJvm", "languageJava", "languageGroovy", "languageScala",
            "platformNative", "platformPlay", "idePlay", "languageNative", "ideNative", "testingBase",
            "testingNative", "testingJvm", "testingJunitPlatform", "pluginDevelopment", "pluginUse", "resourcesHttp",
            "resourcesSftp", "resourcesS3", "resourcesGcs", "compositeBuilds", "buildCacheHttp").map { rootProject.project(it) }.toSet()

    val Project.implementationPluginProjects
        get() = setOf(rootProject.project("toolingApiBuilders"))

    val Project.publishedProjects
        get() = setOf(
            rootProject.project(":logging"),
            rootProject.project(":core"),
            rootProject.project(":modelCore"),
            rootProject.project(":toolingApi"),
            rootProject.project(":wrapper"),
            rootProject.project(":baseServices"),
            rootProject.project(":baseServicesGroovy"),
            rootProject.project(":workers"),
            rootProject.project(":dependencyManagement"),
            rootProject.project(":messaging"),
            rootProject.project(":processServices"),
            rootProject.project(":resources"))

    val Project.projectsRequiringJava8
        get() = setOf(rootProject.project(":testingJunitPlatform"))

    val Project.publicProjects
        get() = pluginProjects +
            implementationPluginProjects +
            publicJavaProjects -
            setOf(":smokeTest", ":soak").map { rootProject.project(it) }
}
