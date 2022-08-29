plugins {
    id("gradlebuild.distribution.api-java")
    id("info.solidsoft.pitest").version("1.7.4")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-groovy"))
    implementation(project(":diagnostics"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":snapshots"))
    implementation(project(":execution")) {
        because("We need it for BuildOutputCleanupRegistry")
    }

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":messaging"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(libs.gson) {
        because("for unknown reason (bug in the Groovy/Spock compiler?) requires it to be present to use the Gradle Module Metadata test fixtures")
    }
    testImplementation(libs.jsoup)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":jvm-services")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))
    testImplementation(testFixtures(project(":language-groovy")))
    testImplementation(testFixtures(project(":diagnostics")))

    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":base-services-groovy"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(project(":language-jvm"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":model-core")))
//    testImplementation(testFixtures(project(":model-core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
//    testImplementation(project(":distributions-jvm"))

    testImplementation(libs.piTest)
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // uses deprecated software model types
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

integTest.usesJavadocCodeSnippets.set(true)
testFilesCleanup.reportOnly.set(true)

description = """Provides core Gradle plugins such as the base plugin and version catalog plugin, as well as JVM-related plugins for building different types of Java and Groovy projects."""

pitest {
    verbose.set(true)

    excludedTestClasses.set(setOf(
            // random examples
            "org.gradle.jvm.GeneratedSourcesDirectorySetContributorIntegrationTest",
            "org.gradle.java.compile.NonIncrementalJavaCompileAvoidanceAgainstJarIntegrationSpec",
            "org.gradle.java.JavaCrossCompilationIntegrationTest",

            // known failures
            "org.gradle.api.plugins.BuildSrcPluginIntegrationTest",
            "org.gradle.api.plugins.JavaPluginIntegrationTest",
            "org.gradle.api.plugins.BuildSrcPluginIntegrationTest" ,
            "org.gradle.api.plugins.JavaPluginIntegrationTest" ,
            "org.gradle.api.plugins.JvmTestSuitePluginIntegrationTest",
            "org.gradle.api.plugins.TestReportAggregationPluginIntegrationTest",
            "org.gradle.api.tasks.JavaExecDebugIntegrationTest",
            "org.gradle.compile.daemon.ParallelCompilerDaemonIntegrationTest",
            "org.gradle.groovy.GroovyJavaLibraryInteractionIntegrationTest",
            "org.gradle.groovy.GroovyLibraryIntegrationTest",
            "org.gradle.groovy.compile.CachedGroovyCompileIntegrationTest",
            "org.gradle.groovy.compile.DaemonGroovyCompilerIntegrationTest",
            "org.gradle.groovy.compile.GroovyJavaJointCompileSourceOrderIntegrationTest",
            "org.gradle.groovy.compile.IncrementalGroovyCompileIntegrationTest",
            "org.gradle.groovy.compile.InvokeDynamicGroovyCompilerSpec",
            "org.gradle.groovy.environment.JreJavaHomeGroovyIntegrationTest",
            "org.gradle.java.JavaLibraryFeatureCompilationIntegrationTest",
            "org.gradle.java.compile.IncrementalGroovyCompileAvoidanceAgainstClassDirIntegrationSpec",
            "org.gradle.java.compile.IncrementalGroovyCompileAvoidanceAgainstJarIntegrationSpec",
            "org.gradle.java.compile.NonIncrementalGroovyCompileAvoidanceAgainstClassDirIntegrationSpec",
            "org.gradle.java.compile.NonIncrementalGroovyCompileAvoidanceAgainstJarIntegrationSpec",
            "org.gradle.java.compile.daemon.DaemonJavaCompilerIntegrationTest",
            "org.gradle.java.environment.JreJavaHomeJavaIntegrationTest",
            "org.gradle.java.fixtures.JavaLibraryTestFixturesIntegrationTest",
            "org.gradle.java.fixtures.JavaTestFixturesIntegrationTest",
            "org.gradle.jvm.JvmVariantBuilderIntegrationTest",
            "org.gradle.jvm.JvmVariantBuilderIntegrationTest",
            "org.gradle.groovy.compile.SameToolchainGroovyCompileIntegrationTest",
            "org.gradle.java.compile.GroovyIncrementalCompileIntegrationTest"
    ))

//  It's NOT this    jvmArgs.set(setOf("-Dorg.gradle.integtest.executer=embedded"))
// or this    mainProcessJvmArgs.set(setOf("-Dorg.gradle.integtest.executer=embedded"))

    jvmArgs.set(setOf("-Dorg.gradle.integtest.executer=embedded",
            "-DintegTest.gradleUserHomeDir=/Users/ttresansky/Projects/gradle/intTestHomeDir/distributions-jvm",
            "-DintegTest.samplesdir=/Users/ttresansky/Projects/gradle/subprojects/docs/src/snippets",
            "-Dorg.gradle.integtest.daemon.registry=/Users/ttresansky/Projects/gradle/build/daemon/distributions-jvm",
            "-DintegTest.distZipVersion=7.6-20220826040000+0000",
            "-DdeclaredSampleInputs=/Users/ttresansky/Projects/gradle/subprojects/plugins/src/main"
            //,"-{agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006}" // for debuggign inner process
    ))
    //mainProcessJvmArgs.set(setOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")) // debugging pitest process

    //testSourceSets.set(setOf(sourceSets.getByName("test")))
    testSourceSets.set(setOf(sourceSets.getByName("integTest")))

    junit5PluginVersion.set("1.0.0")
    targetClasses.set(setOf("org.gradle.*"))  //by default "${project.group}.*"
    pitestVersion.set("1.9.3") //not needed when a default PIT version should be used
    threads.set(8)
    outputFormats.set(setOf("XML", "HTML"))

    exportLineCoverage.set(true)
    timestampedReports.set(false) // disable placing PIT reports in time-based subfolders for reproducibility

    // Allows for incrementatlly running mutation testing
    historyInputLocation.set(project.layout.buildDirectory.file("pit-history"))
    historyOutputLocation.set(project.layout.buildDirectory.file("pit-history"))
}