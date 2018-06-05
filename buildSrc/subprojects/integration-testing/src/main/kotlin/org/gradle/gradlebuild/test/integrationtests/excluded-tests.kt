package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.JavaVersion.VERSION_1_10
import org.gradle.api.JavaVersion.VERSION_1_9


internal
val excludedTests = listOf(
    // Caused by: java.lang.IncompatibleClassChangeError: Method Person.getName()Ljava/lang/String; must be InterfaceMethodref constant
    // Fail since build 125
    "InterfaceBackedManagedTypeIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Broken scala and twirl compilation
    // Play does not fully support Java 9 yet (https://github.com/playframework/playframework/issues/7879)
    "MixedPlayAndJvmLibraryProjectIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayAppWithFailingTestsIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayMultiProjectApplicationIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayPlatformIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayBinaryAdvancedAppIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayDistributionAdvancedAppIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayBinaryBasicAppIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayDistributionBasicAppIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayTestBasicAppIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayContinuousBuildIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayContinuousBuildReloadIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayContinuousBuildReloadWaitingIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayMultiProjectContinuousBuildIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayMultiProjectReloadIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayReloadIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayReloadWaitingIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayTwirlCompilerContinuousIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayBinaryAppWithDependenciesIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayDistributionAppWithDependenciesIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayTestAppWithDependenciesIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "AdvancedPlaySampleIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "BasicPlaySampleIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "MultiprojectPlaySampleIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "UserGuidePlaySamplesIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayApplicationPluginIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "Play23RoutesCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "Play24RoutesCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayAssetsJarIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayRunIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "TwirlCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "TwirlVersionIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayIdeaPluginAdvancedIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayIdeaPluginBasicIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayIdeaPluginMultiprojectIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ProjectLayoutIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "SamplesMixedJavaAndScalaIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "SamplesScalaCustomizedLayoutIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "SamplesScalaQuickstartIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "JointScalaLangIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "SampleScalaLanguageIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaCompileParallelIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaCompilerContinuousIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaLanguageIncrementalBuildIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaLanguageIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaCrossCompilationIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "IncrementalScalaCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ZincScalaCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaTestIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaLibraryInitIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ZincScalaCompilerMultiVersionIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayCompositeBuildIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "PlayJavaAnnotationProcessingIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaAnnotationProcessingIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "CachedScalaCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "CachedPlatformScalaCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaCompileRelocationIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "UpToDateScalaCompileIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaDocIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaCompilerDaemonReuseIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "ScalaComponentCompilerDaemonReuseIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Sample attempts to set max perm space
    // Compilation issue without the JVM setting
    "SamplesScalaZincIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Test compiles for Java 5
    "ToolingApiUnsupportedClientJvmCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10)
)
