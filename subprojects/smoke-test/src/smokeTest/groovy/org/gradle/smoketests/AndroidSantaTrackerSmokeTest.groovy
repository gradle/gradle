/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.smoketests

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginSettingsFixture
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Requires(TestPrecondition.JDK11_OR_EARLIER)
class AndroidSantaTrackerSmokeTest extends AbstractSmokeTest {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder
    TestFile homeDir

    def setup() {
        homeDir = temporaryFolder.createDir("test-kit-home")
    }

    @ToBeFixedForInstantExecution
    def "check deprecation warnings produced by building Santa Tracker"() {
        def checkoutDir = temporaryFolder.createDir ("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        def result = buildLocation(checkoutDir)

        expect:
        expectDeprecationWarnings(result,
            "The configuration :detachedConfiguration1 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration10 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration11 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration12 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration13 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration14 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration15 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration2 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration3 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration4 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration5 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration6 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration7 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration8 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "The configuration :detachedConfiguration9 was resolved without accessing the project in a safe manner.  This may happen when a configuration is resolved from a different project.  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
        )
    }

    @ToBeFixedForInstantExecution
    def "can cache Santa Tracker Android application"() {
        def originalDir = temporaryFolder.createDir ("original")
        def relocatedDir = temporaryFolder.createDir("relocated")

        setupCopyOfSantaTracker(originalDir)
        setupCopyOfSantaTracker(relocatedDir)

        buildLocation(originalDir)
        BuildResult relocatedResult = buildLocation(relocatedDir)

        expect:
        verify(relocatedResult, EXPECTED_RESULTS)
    }

    @ToBeFixedForInstantExecution
    def "incremental Java compilation works for Santa Tracker"() {
        def checkoutDir = temporaryFolder.createDir ("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        def pathToClass = "com/google/android/apps/santatracker/tracker/ui/BottomSheetBehavior"
        def fileToChange = checkoutDir.file("tracker/src/main/java/${pathToClass}.java")
        def compiledClassFile = checkoutDir.file("tracker/build/intermediates/javac/debug/classes/${pathToClass}.class")
        def nonAbiChangeMutator = new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)

        when:
        def result = buildLocation(checkoutDir)
        def md5Before = compiledClassFile.md5Hash
        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS

        when:
        nonAbiChangeMutator.beforeBuild()
        buildLocation(checkoutDir)
        def md5After = compiledClassFile.md5Hash
        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        md5After != md5Before
    }

    private void setupCopyOfSantaTracker(TestFile targetDir) {
        copyRemoteProject("santaTracker", targetDir)
        GradleEnterprisePluginSettingsFixture.applyEnterprisePlugin(targetDir.file("settings.gradle"))
    }

    private BuildResult buildLocation(File projectDir) {
        runner("assembleDebug")
            .withProjectDir(projectDir)
            .withTestKitDir(homeDir)
            .forwardOutput()
            .build()
    }

    private static boolean verify(BuildResult result, Map<String, TaskOutcome> outcomes) {
        println "> Expecting ${outcomes.size()} tasks with outcomes:"
        outcomes.values().groupBy { it }.sort().forEach { outcome, instances -> println "> - $outcome: ${instances.size()}" }

        def outcomesWithMatchingTasks = outcomes.findAll { result.task(it.key) }
        def hasMatchingTasks = outcomesWithMatchingTasks.size() == outcomes.size() && outcomesWithMatchingTasks.size() == result.tasks.size()
        if (!hasMatchingTasks) {
            println "> Tasks missing:    " + (outcomes.findAll { !outcomesWithMatchingTasks.keySet().contains(it.key) })
            println "> Tasks in surplus: " + (result.tasks.findAll { !outcomesWithMatchingTasks.keySet().contains(it.path) })
            println "> Updated definitions:"
            result.tasks
                .toSorted { a, b -> a.path <=> b.path }
                .forEach { task ->
                    println "'${task.path}': ${task.outcome}"
                }
        }

        boolean allOutcomesMatched = true
        outcomesWithMatchingTasks.each { taskName, expectedOutcome ->
            def taskOutcome = result.task(taskName)?.outcome
            if (taskOutcome != expectedOutcome) {
                println "> Task '$taskName' was $taskOutcome but should have been $expectedOutcome"
                allOutcomesMatched = false
            }
        }
        return hasMatchingTasks && allOutcomesMatched
    }

    private static final EXPECTED_RESULTS = [
        ':cityquiz:assembleDebug': SUCCESS,
        ':cityquiz:checkDebugDuplicateClasses': FROM_CACHE,
        ':cityquiz:compileDebugAidl': NO_SOURCE,
        ':cityquiz:compileDebugJavaWithJavac': FROM_CACHE,
        ':cityquiz:compileDebugKotlin': FROM_CACHE,
        ':cityquiz:compileDebugRenderscript': NO_SOURCE,
        ':cityquiz:compileDebugShaders': FROM_CACHE,
        ':cityquiz:compileDebugSources': UP_TO_DATE,
        ':cityquiz:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':cityquiz:dexBuilderDebug': FROM_CACHE,
        ':cityquiz:extractDeepLinksDebug': FROM_CACHE,
        ':cityquiz:featureDebugWriter': SUCCESS,
        ':cityquiz:generateDebugAssets': UP_TO_DATE,
        ':cityquiz:generateDebugBuildConfig': FROM_CACHE,
        ':cityquiz:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':cityquiz:generateDebugResValues': FROM_CACHE,
        ':cityquiz:generateDebugResources': UP_TO_DATE,
        ':cityquiz:javaPreCompileDebug': FROM_CACHE,
        ':cityquiz:mainApkListPersistenceDebug': FROM_CACHE,
        ':cityquiz:mergeDebugAssets': FROM_CACHE,
        ':cityquiz:mergeDebugJavaResource': SUCCESS,
        ':cityquiz:mergeDebugJniLibFolders': FROM_CACHE,
        ':cityquiz:mergeDebugNativeLibs': FROM_CACHE,
        ':cityquiz:mergeDebugResources': FROM_CACHE,
        ':cityquiz:mergeDebugShaders': FROM_CACHE,
        ':cityquiz:mergeExtDexDebug': FROM_CACHE,
        ':cityquiz:mergeLibDexDebug': FROM_CACHE,
        ':cityquiz:mergeProjectDexDebug': FROM_CACHE,
        ':cityquiz:packageDebug': SUCCESS,
        ':cityquiz:preBuild': UP_TO_DATE,
        ':cityquiz:preDebugBuild': UP_TO_DATE,
        ':cityquiz:processDebugJavaRes': NO_SOURCE,
        ':cityquiz:processDebugManifest': FROM_CACHE,
        ':cityquiz:processDebugResources': FROM_CACHE,
        ':cityquiz:stripDebugDebugSymbols': FROM_CACHE,
        ':common:assembleDebug': SUCCESS,
        ':common:bundleDebugAar': SUCCESS,
        ':common:bundleLibCompileDebug': SUCCESS,
        ':common:bundleLibResDebug': SUCCESS,
        ':common:bundleLibRuntimeDebug': SUCCESS,
        ':common:compileDebugAidl': NO_SOURCE,
        ':common:compileDebugJavaWithJavac': FROM_CACHE,
        ':common:compileDebugKotlin': FROM_CACHE,
        ':common:compileDebugLibraryResources': FROM_CACHE,
        ':common:compileDebugRenderscript': NO_SOURCE,
        ':common:compileDebugShaders': FROM_CACHE,
        ':common:compileDebugSources': UP_TO_DATE,
        ':common:copyDebugJniLibsProjectAndLocalJars': FROM_CACHE,
        ':common:copyDebugJniLibsProjectOnly': FROM_CACHE,
        ':common:createFullJarDebug': FROM_CACHE,
        ':common:extractDebugAnnotations': FROM_CACHE,
        ':common:extractDeepLinksDebug': FROM_CACHE,
        ':common:generateDebugAssets': UP_TO_DATE,
        ':common:generateDebugBuildConfig': FROM_CACHE,
        ':common:generateDebugRFile': FROM_CACHE,
        ':common:generateDebugResValues': FROM_CACHE,
        ':common:generateDebugResources': UP_TO_DATE,
        ':common:javaPreCompileDebug': FROM_CACHE,
        ':common:mergeDebugConsumerProguardFiles': SUCCESS,
        ':common:mergeDebugGeneratedProguardFiles': SUCCESS,
        ':common:mergeDebugJavaResource': SUCCESS,
        ':common:mergeDebugJniLibFolders': FROM_CACHE,
        ':common:mergeDebugNativeLibs': FROM_CACHE,
        ':common:mergeDebugShaders': FROM_CACHE,
        ':common:packageDebugAssets': FROM_CACHE,
        ':common:packageDebugRenderscript': NO_SOURCE,
        ':common:packageDebugResources': FROM_CACHE,
        ':common:parseDebugLocalResources': FROM_CACHE,
        ':common:preBuild': UP_TO_DATE,
        ':common:preDebugBuild': UP_TO_DATE,
        ':common:prepareLintJarForPublish': SUCCESS,
        ':common:processDebugJavaRes': NO_SOURCE,
        ':common:processDebugManifest': FROM_CACHE,
        ':common:stripDebugDebugSymbols': FROM_CACHE,
        ':common:syncDebugLibJars': SUCCESS,
        ':dasherdancer:assembleDebug': SUCCESS,
        ':dasherdancer:checkDebugDuplicateClasses': FROM_CACHE,
        ':dasherdancer:compileDebugAidl': NO_SOURCE,
        ':dasherdancer:compileDebugJavaWithJavac': FROM_CACHE,
        ':dasherdancer:compileDebugKotlin': FROM_CACHE,
        ':dasherdancer:compileDebugRenderscript': NO_SOURCE,
        ':dasherdancer:compileDebugShaders': FROM_CACHE,
        ':dasherdancer:compileDebugSources': UP_TO_DATE,
        ':dasherdancer:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':dasherdancer:dexBuilderDebug': FROM_CACHE,
        ':dasherdancer:extractDeepLinksDebug': FROM_CACHE,
        ':dasherdancer:featureDebugWriter': SUCCESS,
        ':dasherdancer:generateDebugAssets': UP_TO_DATE,
        ':dasherdancer:generateDebugBuildConfig': FROM_CACHE,
        ':dasherdancer:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':dasherdancer:generateDebugResValues': FROM_CACHE,
        ':dasherdancer:generateDebugResources': UP_TO_DATE,
        ':dasherdancer:javaPreCompileDebug': FROM_CACHE,
        ':dasherdancer:mainApkListPersistenceDebug': FROM_CACHE,
        ':dasherdancer:mergeDebugAssets': FROM_CACHE,
        ':dasherdancer:mergeDebugJavaResource': SUCCESS,
        ':dasherdancer:mergeDebugJniLibFolders': FROM_CACHE,
        ':dasherdancer:mergeDebugNativeLibs': FROM_CACHE,
        ':dasherdancer:mergeDebugResources': FROM_CACHE,
        ':dasherdancer:mergeDebugShaders': FROM_CACHE,
        ':dasherdancer:mergeExtDexDebug': FROM_CACHE,
        ':dasherdancer:mergeLibDexDebug': FROM_CACHE,
        ':dasherdancer:mergeProjectDexDebug': FROM_CACHE,
        ':dasherdancer:packageDebug': SUCCESS,
        ':dasherdancer:preBuild': UP_TO_DATE,
        ':dasherdancer:preDebugBuild': UP_TO_DATE,
        ':dasherdancer:processDebugJavaRes': NO_SOURCE,
        ':dasherdancer:processDebugManifest': FROM_CACHE,
        ':dasherdancer:processDebugResources': FROM_CACHE,
        ':dasherdancer:stripDebugDebugSymbols': FROM_CACHE,
        ':doodles-lib:assembleDebug': SUCCESS,
        ':doodles-lib:bundleDebugAar': SUCCESS,
        ':doodles-lib:bundleLibCompileDebug': SUCCESS,
        ':doodles-lib:bundleLibResDebug': SUCCESS,
        ':doodles-lib:bundleLibRuntimeDebug': SUCCESS,
        ':doodles-lib:compileDebugAidl': NO_SOURCE,
        ':doodles-lib:compileDebugJavaWithJavac': FROM_CACHE,
        ':doodles-lib:compileDebugLibraryResources': FROM_CACHE,
        ':doodles-lib:compileDebugRenderscript': NO_SOURCE,
        ':doodles-lib:compileDebugShaders': FROM_CACHE,
        ':doodles-lib:compileDebugSources': UP_TO_DATE,
        ':doodles-lib:copyDebugJniLibsProjectAndLocalJars': FROM_CACHE,
        ':doodles-lib:copyDebugJniLibsProjectOnly': FROM_CACHE,
        ':doodles-lib:createFullJarDebug': FROM_CACHE,
        ':doodles-lib:extractDebugAnnotations': FROM_CACHE,
        ':doodles-lib:extractDeepLinksDebug': FROM_CACHE,
        ':doodles-lib:generateDebugAssets': UP_TO_DATE,
        ':doodles-lib:generateDebugBuildConfig': FROM_CACHE,
        ':doodles-lib:generateDebugRFile': FROM_CACHE,
        ':doodles-lib:generateDebugResValues': FROM_CACHE,
        ':doodles-lib:generateDebugResources': UP_TO_DATE,
        ':doodles-lib:javaPreCompileDebug': FROM_CACHE,
        ':doodles-lib:mergeDebugConsumerProguardFiles': SUCCESS,
        ':doodles-lib:mergeDebugGeneratedProguardFiles': SUCCESS,
        ':doodles-lib:mergeDebugJavaResource': FROM_CACHE,
        ':doodles-lib:mergeDebugJniLibFolders': FROM_CACHE,
        ':doodles-lib:mergeDebugNativeLibs': FROM_CACHE,
        ':doodles-lib:mergeDebugShaders': FROM_CACHE,
        ':doodles-lib:packageDebugAssets': FROM_CACHE,
        ':doodles-lib:packageDebugRenderscript': NO_SOURCE,
        ':doodles-lib:packageDebugResources': FROM_CACHE,
        ':doodles-lib:parseDebugLocalResources': FROM_CACHE,
        ':doodles-lib:preBuild': UP_TO_DATE,
        ':doodles-lib:preDebugBuild': UP_TO_DATE,
        ':doodles-lib:prepareLintJarForPublish': SUCCESS,
        ':doodles-lib:processDebugJavaRes': NO_SOURCE,
        ':doodles-lib:processDebugManifest': FROM_CACHE,
        ':doodles-lib:stripDebugDebugSymbols': FROM_CACHE,
        ':doodles-lib:syncDebugLibJars': FROM_CACHE,
        ':gumball:assembleDebug': SUCCESS,
        ':gumball:checkDebugDuplicateClasses': FROM_CACHE,
        ':gumball:compileDebugAidl': NO_SOURCE,
        ':gumball:compileDebugJavaWithJavac': FROM_CACHE,
        ':gumball:compileDebugRenderscript': NO_SOURCE,
        ':gumball:compileDebugShaders': FROM_CACHE,
        ':gumball:compileDebugSources': UP_TO_DATE,
        ':gumball:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':gumball:dexBuilderDebug': FROM_CACHE,
        ':gumball:extractDeepLinksDebug': FROM_CACHE,
        ':gumball:featureDebugWriter': SUCCESS,
        ':gumball:generateDebugAssets': UP_TO_DATE,
        ':gumball:generateDebugBuildConfig': FROM_CACHE,
        ':gumball:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':gumball:generateDebugResValues': FROM_CACHE,
        ':gumball:generateDebugResources': UP_TO_DATE,
        ':gumball:javaPreCompileDebug': FROM_CACHE,
        ':gumball:mainApkListPersistenceDebug': FROM_CACHE,
        ':gumball:mergeDebugAssets': FROM_CACHE,
        ':gumball:mergeDebugJavaResource': FROM_CACHE,
        ':gumball:mergeDebugJniLibFolders': FROM_CACHE,
        ':gumball:mergeDebugNativeLibs': FROM_CACHE,
        ':gumball:mergeDebugResources': FROM_CACHE,
        ':gumball:mergeDebugShaders': FROM_CACHE,
        ':gumball:mergeExtDexDebug': FROM_CACHE,
        ':gumball:mergeLibDexDebug': FROM_CACHE,
        ':gumball:mergeProjectDexDebug': FROM_CACHE,
        ':gumball:packageDebug': SUCCESS,
        ':gumball:preBuild': UP_TO_DATE,
        ':gumball:preDebugBuild': UP_TO_DATE,
        ':gumball:processDebugJavaRes': NO_SOURCE,
        ':gumball:processDebugManifest': FROM_CACHE,
        ':gumball:processDebugResources': FROM_CACHE,
        ':gumball:stripDebugDebugSymbols': FROM_CACHE,
        ':jetpack:assembleDebug': SUCCESS,
        ':jetpack:checkDebugDuplicateClasses': FROM_CACHE,
        ':jetpack:compileDebugAidl': NO_SOURCE,
        ':jetpack:compileDebugJavaWithJavac': FROM_CACHE,
        ':jetpack:compileDebugKotlin': FROM_CACHE,
        ':jetpack:compileDebugRenderscript': NO_SOURCE,
        ':jetpack:compileDebugShaders': FROM_CACHE,
        ':jetpack:compileDebugSources': UP_TO_DATE,
        ':jetpack:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':jetpack:dexBuilderDebug': FROM_CACHE,
        ':jetpack:extractDeepLinksDebug': FROM_CACHE,
        ':jetpack:featureDebugWriter': SUCCESS,
        ':jetpack:generateDebugAssets': UP_TO_DATE,
        ':jetpack:generateDebugBuildConfig': FROM_CACHE,
        ':jetpack:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':jetpack:generateDebugResValues': FROM_CACHE,
        ':jetpack:generateDebugResources': UP_TO_DATE,
        ':jetpack:javaPreCompileDebug': FROM_CACHE,
        ':jetpack:mainApkListPersistenceDebug': FROM_CACHE,
        ':jetpack:mergeDebugAssets': FROM_CACHE,
        ':jetpack:mergeDebugJavaResource': SUCCESS,
        ':jetpack:mergeDebugJniLibFolders': FROM_CACHE,
        ':jetpack:mergeDebugNativeLibs': FROM_CACHE,
        ':jetpack:mergeDebugResources': FROM_CACHE,
        ':jetpack:mergeDebugShaders': FROM_CACHE,
        ':jetpack:mergeExtDexDebug': FROM_CACHE,
        ':jetpack:mergeLibDexDebug': FROM_CACHE,
        ':jetpack:mergeProjectDexDebug': FROM_CACHE,
        ':jetpack:packageDebug': SUCCESS,
        ':jetpack:preBuild': UP_TO_DATE,
        ':jetpack:preDebugBuild': UP_TO_DATE,
        ':jetpack:processDebugJavaRes': NO_SOURCE,
        ':jetpack:processDebugManifest': FROM_CACHE,
        ':jetpack:processDebugResources': FROM_CACHE,
        ':jetpack:stripDebugDebugSymbols': FROM_CACHE,
        ':memory:assembleDebug': SUCCESS,
        ':memory:checkDebugDuplicateClasses': FROM_CACHE,
        ':memory:compileDebugAidl': NO_SOURCE,
        ':memory:compileDebugJavaWithJavac': FROM_CACHE,
        ':memory:compileDebugRenderscript': NO_SOURCE,
        ':memory:compileDebugShaders': FROM_CACHE,
        ':memory:compileDebugSources': UP_TO_DATE,
        ':memory:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':memory:dexBuilderDebug': FROM_CACHE,
        ':memory:extractDeepLinksDebug': FROM_CACHE,
        ':memory:featureDebugWriter': SUCCESS,
        ':memory:generateDebugAssets': UP_TO_DATE,
        ':memory:generateDebugBuildConfig': FROM_CACHE,
        ':memory:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':memory:generateDebugResValues': FROM_CACHE,
        ':memory:generateDebugResources': UP_TO_DATE,
        ':memory:javaPreCompileDebug': FROM_CACHE,
        ':memory:mainApkListPersistenceDebug': FROM_CACHE,
        ':memory:mergeDebugAssets': FROM_CACHE,
        ':memory:mergeDebugJavaResource': FROM_CACHE,
        ':memory:mergeDebugJniLibFolders': FROM_CACHE,
        ':memory:mergeDebugNativeLibs': FROM_CACHE,
        ':memory:mergeDebugResources': FROM_CACHE,
        ':memory:mergeDebugShaders': FROM_CACHE,
        ':memory:mergeExtDexDebug': FROM_CACHE,
        ':memory:mergeLibDexDebug': FROM_CACHE,
        ':memory:mergeProjectDexDebug': FROM_CACHE,
        ':memory:packageDebug': SUCCESS,
        ':memory:preBuild': UP_TO_DATE,
        ':memory:preDebugBuild': UP_TO_DATE,
        ':memory:processDebugJavaRes': NO_SOURCE,
        ':memory:processDebugManifest': FROM_CACHE,
        ':memory:processDebugResources': FROM_CACHE,
        ':memory:stripDebugDebugSymbols': FROM_CACHE,
        ':penguinswim:assembleDebug': SUCCESS,
        ':penguinswim:checkDebugDuplicateClasses': FROM_CACHE,
        ':penguinswim:compileDebugAidl': NO_SOURCE,
        ':penguinswim:compileDebugJavaWithJavac': FROM_CACHE,
        ':penguinswim:compileDebugRenderscript': NO_SOURCE,
        ':penguinswim:compileDebugShaders': FROM_CACHE,
        ':penguinswim:compileDebugSources': UP_TO_DATE,
        ':penguinswim:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':penguinswim:dexBuilderDebug': FROM_CACHE,
        ':penguinswim:extractDeepLinksDebug': FROM_CACHE,
        ':penguinswim:featureDebugWriter': SUCCESS,
        ':penguinswim:generateDebugAssets': UP_TO_DATE,
        ':penguinswim:generateDebugBuildConfig': FROM_CACHE,
        ':penguinswim:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':penguinswim:generateDebugResValues': FROM_CACHE,
        ':penguinswim:generateDebugResources': UP_TO_DATE,
        ':penguinswim:javaPreCompileDebug': FROM_CACHE,
        ':penguinswim:mainApkListPersistenceDebug': FROM_CACHE,
        ':penguinswim:mergeDebugAssets': FROM_CACHE,
        ':penguinswim:mergeDebugJavaResource': FROM_CACHE,
        ':penguinswim:mergeDebugJniLibFolders': FROM_CACHE,
        ':penguinswim:mergeDebugNativeLibs': FROM_CACHE,
        ':penguinswim:mergeDebugResources': FROM_CACHE,
        ':penguinswim:mergeDebugShaders': FROM_CACHE,
        ':penguinswim:mergeExtDexDebug': FROM_CACHE,
        ':penguinswim:mergeLibDexDebug': FROM_CACHE,
        ':penguinswim:mergeProjectDexDebug': FROM_CACHE,
        ':penguinswim:packageDebug': SUCCESS,
        ':penguinswim:preBuild': UP_TO_DATE,
        ':penguinswim:preDebugBuild': UP_TO_DATE,
        ':penguinswim:processDebugJavaRes': NO_SOURCE,
        ':penguinswim:processDebugManifest': FROM_CACHE,
        ':penguinswim:processDebugResources': FROM_CACHE,
        ':penguinswim:stripDebugDebugSymbols': FROM_CACHE,
        ':playgames:assembleDebug': SUCCESS,
        ':playgames:bundleDebugAar': SUCCESS,
        ':playgames:bundleLibCompileDebug': SUCCESS,
        ':playgames:bundleLibResDebug': SUCCESS,
        ':playgames:bundleLibRuntimeDebug': SUCCESS,
        ':playgames:compileDebugAidl': NO_SOURCE,
        ':playgames:compileDebugJavaWithJavac': FROM_CACHE,
        ':playgames:compileDebugLibraryResources': FROM_CACHE,
        ':playgames:compileDebugRenderscript': NO_SOURCE,
        ':playgames:compileDebugShaders': FROM_CACHE,
        ':playgames:compileDebugSources': UP_TO_DATE,
        ':playgames:copyDebugJniLibsProjectAndLocalJars': FROM_CACHE,
        ':playgames:copyDebugJniLibsProjectOnly': FROM_CACHE,
        ':playgames:createFullJarDebug': FROM_CACHE,
        ':playgames:extractDebugAnnotations': FROM_CACHE,
        ':playgames:extractDeepLinksDebug': FROM_CACHE,
        ':playgames:generateDebugAssets': UP_TO_DATE,
        ':playgames:generateDebugBuildConfig': FROM_CACHE,
        ':playgames:generateDebugRFile': FROM_CACHE,
        ':playgames:generateDebugResValues': FROM_CACHE,
        ':playgames:generateDebugResources': UP_TO_DATE,
        ':playgames:javaPreCompileDebug': FROM_CACHE,
        ':playgames:mergeDebugConsumerProguardFiles': SUCCESS,
        ':playgames:mergeDebugGeneratedProguardFiles': SUCCESS,
        ':playgames:mergeDebugJavaResource': FROM_CACHE,
        ':playgames:mergeDebugJniLibFolders': FROM_CACHE,
        ':playgames:mergeDebugNativeLibs': FROM_CACHE,
        ':playgames:mergeDebugShaders': FROM_CACHE,
        ':playgames:packageDebugAssets': FROM_CACHE,
        ':playgames:packageDebugRenderscript': NO_SOURCE,
        ':playgames:packageDebugResources': FROM_CACHE,
        ':playgames:parseDebugLocalResources': FROM_CACHE,
        ':playgames:preBuild': UP_TO_DATE,
        ':playgames:preDebugBuild': UP_TO_DATE,
        ':playgames:prepareLintJarForPublish': SUCCESS,
        ':playgames:processDebugJavaRes': NO_SOURCE,
        ':playgames:processDebugManifest': FROM_CACHE,
        ':playgames:stripDebugDebugSymbols': FROM_CACHE,
        ':playgames:syncDebugLibJars': FROM_CACHE,
        ':presenttoss:assembleDebug': SUCCESS,
        ':presenttoss:checkDebugDuplicateClasses': FROM_CACHE,
        ':presenttoss:compileDebugAidl': NO_SOURCE,
        ':presenttoss:compileDebugJavaWithJavac': FROM_CACHE,
        ':presenttoss:compileDebugRenderscript': NO_SOURCE,
        ':presenttoss:compileDebugShaders': FROM_CACHE,
        ':presenttoss:compileDebugSources': UP_TO_DATE,
        ':presenttoss:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':presenttoss:dexBuilderDebug': FROM_CACHE,
        ':presenttoss:extractDeepLinksDebug': FROM_CACHE,
        ':presenttoss:featureDebugWriter': SUCCESS,
        ':presenttoss:generateDebugAssets': UP_TO_DATE,
        ':presenttoss:generateDebugBuildConfig': FROM_CACHE,
        ':presenttoss:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':presenttoss:generateDebugResValues': FROM_CACHE,
        ':presenttoss:generateDebugResources': UP_TO_DATE,
        ':presenttoss:javaPreCompileDebug': FROM_CACHE,
        ':presenttoss:mainApkListPersistenceDebug': FROM_CACHE,
        ':presenttoss:mergeDebugAssets': FROM_CACHE,
        ':presenttoss:mergeDebugJavaResource': FROM_CACHE,
        ':presenttoss:mergeDebugJniLibFolders': FROM_CACHE,
        ':presenttoss:mergeDebugNativeLibs': FROM_CACHE,
        ':presenttoss:mergeDebugResources': FROM_CACHE,
        ':presenttoss:mergeDebugShaders': FROM_CACHE,
        ':presenttoss:mergeExtDexDebug': FROM_CACHE,
        ':presenttoss:mergeLibDexDebug': FROM_CACHE,
        ':presenttoss:mergeProjectDexDebug': FROM_CACHE,
        ':presenttoss:packageDebug': SUCCESS,
        ':presenttoss:preBuild': UP_TO_DATE,
        ':presenttoss:preDebugBuild': UP_TO_DATE,
        ':presenttoss:processDebugJavaRes': NO_SOURCE,
        ':presenttoss:processDebugManifest': FROM_CACHE,
        ':presenttoss:processDebugResources': FROM_CACHE,
        ':presenttoss:stripDebugDebugSymbols': FROM_CACHE,
        ':rocketsleigh:assembleDebug': SUCCESS,
        ':rocketsleigh:checkDebugDuplicateClasses': FROM_CACHE,
        ':rocketsleigh:compileDebugAidl': NO_SOURCE,
        ':rocketsleigh:compileDebugJavaWithJavac': FROM_CACHE,
        ':rocketsleigh:compileDebugKotlin': FROM_CACHE,
        ':rocketsleigh:compileDebugRenderscript': NO_SOURCE,
        ':rocketsleigh:compileDebugShaders': FROM_CACHE,
        ':rocketsleigh:compileDebugSources': UP_TO_DATE,
        ':rocketsleigh:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':rocketsleigh:dexBuilderDebug': FROM_CACHE,
        ':rocketsleigh:extractDeepLinksDebug': FROM_CACHE,
        ':rocketsleigh:featureDebugWriter': SUCCESS,
        ':rocketsleigh:generateDebugAssets': UP_TO_DATE,
        ':rocketsleigh:generateDebugBuildConfig': FROM_CACHE,
        ':rocketsleigh:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':rocketsleigh:generateDebugResValues': FROM_CACHE,
        ':rocketsleigh:generateDebugResources': UP_TO_DATE,
        ':rocketsleigh:javaPreCompileDebug': FROM_CACHE,
        ':rocketsleigh:mainApkListPersistenceDebug': FROM_CACHE,
        ':rocketsleigh:mergeDebugAssets': FROM_CACHE,
        ':rocketsleigh:mergeDebugJavaResource': SUCCESS,
        ':rocketsleigh:mergeDebugJniLibFolders': FROM_CACHE,
        ':rocketsleigh:mergeDebugNativeLibs': FROM_CACHE,
        ':rocketsleigh:mergeDebugResources': FROM_CACHE,
        ':rocketsleigh:mergeDebugShaders': FROM_CACHE,
        ':rocketsleigh:mergeExtDexDebug': FROM_CACHE,
        ':rocketsleigh:mergeLibDexDebug': FROM_CACHE,
        ':rocketsleigh:mergeProjectDexDebug': FROM_CACHE,
        ':rocketsleigh:packageDebug': SUCCESS,
        ':rocketsleigh:preBuild': UP_TO_DATE,
        ':rocketsleigh:preDebugBuild': UP_TO_DATE,
        ':rocketsleigh:processDebugJavaRes': NO_SOURCE,
        ':rocketsleigh:processDebugManifest': FROM_CACHE,
        ':rocketsleigh:processDebugResources': FROM_CACHE,
        ':rocketsleigh:stripDebugDebugSymbols': FROM_CACHE,
        ':santa-tracker:assembleDebug': SUCCESS,
        ':santa-tracker:bundleDebugClasses': SUCCESS,
        ':santa-tracker:checkDebugDuplicateClasses': FROM_CACHE,
        ':santa-tracker:checkDebugLibraries': FROM_CACHE,
        ':santa-tracker:compileDebugAidl': NO_SOURCE,
        ':santa-tracker:compileDebugJavaWithJavac': FROM_CACHE,
        ':santa-tracker:compileDebugKotlin': FROM_CACHE,
        ':santa-tracker:compileDebugRenderscript': NO_SOURCE,
        ':santa-tracker:compileDebugShaders': FROM_CACHE,
        ':santa-tracker:compileDebugSources': UP_TO_DATE,
        ':santa-tracker:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':santa-tracker:dexBuilderDebug': FROM_CACHE,
        ':santa-tracker:extractDeepLinksDebug': FROM_CACHE,
        ':santa-tracker:generateDebugAssets': UP_TO_DATE,
        ':santa-tracker:generateDebugBuildConfig': FROM_CACHE,
        ':santa-tracker:generateDebugFeatureMetadata': FROM_CACHE,
        ':santa-tracker:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':santa-tracker:generateDebugResValues': FROM_CACHE,
        ':santa-tracker:generateDebugResources': SUCCESS,
        ':santa-tracker:handleDebugMicroApk': SUCCESS,
        ':santa-tracker:javaPreCompileDebug': FROM_CACHE,
        ':santa-tracker:kaptDebugKotlin': SUCCESS,
        ':santa-tracker:kaptGenerateStubsDebugKotlin': FROM_CACHE,
        ':santa-tracker:mainApkListPersistenceDebug': FROM_CACHE,
        ':santa-tracker:mergeDebugAssets': FROM_CACHE,
        ':santa-tracker:mergeDebugJavaResource': SUCCESS,
        ':santa-tracker:mergeDebugJniLibFolders': FROM_CACHE,
        ':santa-tracker:mergeDebugNativeLibs': FROM_CACHE,
        ':santa-tracker:mergeDebugResources': FROM_CACHE,
        ':santa-tracker:mergeDebugShaders': FROM_CACHE,
        ':santa-tracker:mergeExtDexDebug': FROM_CACHE,
        ':santa-tracker:mergeLibDexDebug': FROM_CACHE,
        ':santa-tracker:mergeProjectDexDebug': FROM_CACHE,
        ':santa-tracker:packageDebug': SUCCESS,
        ':santa-tracker:preBuild': UP_TO_DATE,
        ':santa-tracker:preDebugBuild': FROM_CACHE,
        ':santa-tracker:processDebugJavaRes': NO_SOURCE,
        ':santa-tracker:processDebugManifest': FROM_CACHE,
        ':santa-tracker:processDebugResources': FROM_CACHE,
        ':santa-tracker:signingConfigWriterDebug': FROM_CACHE,
        ':santa-tracker:stripDebugDebugSymbols': FROM_CACHE,
        ':santa-tracker:validateSigningDebug': FROM_CACHE,
        ':santa-tracker:writeDebugModuleMetadata': SUCCESS,
        ':snowballrun:assembleDebug': SUCCESS,
        ':snowballrun:checkDebugDuplicateClasses': FROM_CACHE,
        ':snowballrun:compileDebugAidl': NO_SOURCE,
        ':snowballrun:compileDebugJavaWithJavac': FROM_CACHE,
        ':snowballrun:compileDebugRenderscript': NO_SOURCE,
        ':snowballrun:compileDebugShaders': FROM_CACHE,
        ':snowballrun:compileDebugSources': UP_TO_DATE,
        ':snowballrun:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':snowballrun:dexBuilderDebug': FROM_CACHE,
        ':snowballrun:extractDeepLinksDebug': FROM_CACHE,
        ':snowballrun:featureDebugWriter': SUCCESS,
        ':snowballrun:generateDebugAssets': UP_TO_DATE,
        ':snowballrun:generateDebugBuildConfig': FROM_CACHE,
        ':snowballrun:generateDebugFeatureTransitiveDeps': FROM_CACHE,
        ':snowballrun:generateDebugResValues': FROM_CACHE,
        ':snowballrun:generateDebugResources': UP_TO_DATE,
        ':snowballrun:javaPreCompileDebug': FROM_CACHE,
        ':snowballrun:mainApkListPersistenceDebug': FROM_CACHE,
        ':snowballrun:mergeDebugAssets': FROM_CACHE,
        ':snowballrun:mergeDebugJavaResource': FROM_CACHE,
        ':snowballrun:mergeDebugJniLibFolders': FROM_CACHE,
        ':snowballrun:mergeDebugNativeLibs': FROM_CACHE,
        ':snowballrun:mergeDebugResources': FROM_CACHE,
        ':snowballrun:mergeDebugShaders': FROM_CACHE,
        ':snowballrun:mergeExtDexDebug': FROM_CACHE,
        ':snowballrun:mergeLibDexDebug': FROM_CACHE,
        ':snowballrun:mergeProjectDexDebug': FROM_CACHE,
        ':snowballrun:packageDebug': SUCCESS,
        ':snowballrun:preBuild': UP_TO_DATE,
        ':snowballrun:preDebugBuild': UP_TO_DATE,
        ':snowballrun:processDebugJavaRes': NO_SOURCE,
        ':snowballrun:processDebugManifest': FROM_CACHE,
        ':snowballrun:processDebugResources': FROM_CACHE,
        ':snowballrun:stripDebugDebugSymbols': FROM_CACHE,
        ':tracker:assembleDebug': SUCCESS,
        ':tracker:bundleDebugAar': SUCCESS,
        ':tracker:bundleLibCompileDebug': SUCCESS,
        ':tracker:bundleLibResDebug': SUCCESS,
        ':tracker:bundleLibRuntimeDebug': SUCCESS,
        ':tracker:compileDebugAidl': NO_SOURCE,
        ':tracker:compileDebugJavaWithJavac': SUCCESS,
        ':tracker:compileDebugKotlin': FROM_CACHE,
        ':tracker:compileDebugLibraryResources': FROM_CACHE,
        ':tracker:compileDebugRenderscript': NO_SOURCE,
        ':tracker:compileDebugShaders': FROM_CACHE,
        ':tracker:compileDebugSources': SUCCESS,
        ':tracker:copyDebugJniLibsProjectAndLocalJars': FROM_CACHE,
        ':tracker:copyDebugJniLibsProjectOnly': FROM_CACHE,
        ':tracker:createFullJarDebug': FROM_CACHE,
        ':tracker:extractDebugAnnotations': FROM_CACHE,
        ':tracker:extractDeepLinksDebug': FROM_CACHE,
        ':tracker:generateDebugAssets': UP_TO_DATE,
        ':tracker:generateDebugBuildConfig': FROM_CACHE,
        ':tracker:generateDebugRFile': FROM_CACHE,
        ':tracker:generateDebugResValues': FROM_CACHE,
        ':tracker:generateDebugResources': UP_TO_DATE,
        ':tracker:javaPreCompileDebug': FROM_CACHE,
        ':tracker:kaptDebugKotlin': SUCCESS,
        ':tracker:kaptGenerateStubsDebugKotlin': SUCCESS,
        ':tracker:mergeDebugConsumerProguardFiles': SUCCESS,
        ':tracker:mergeDebugGeneratedProguardFiles': SUCCESS,
        ':tracker:mergeDebugJavaResource': SUCCESS,
        ':tracker:mergeDebugJniLibFolders': FROM_CACHE,
        ':tracker:mergeDebugNativeLibs': FROM_CACHE,
        ':tracker:mergeDebugResources': FROM_CACHE,
        ':tracker:mergeDebugShaders': FROM_CACHE,
        ':tracker:packageDebugAssets': FROM_CACHE,
        ':tracker:packageDebugRenderscript': NO_SOURCE,
        ':tracker:packageDebugResources': FROM_CACHE,
        ':tracker:parseDebugLocalResources': FROM_CACHE,
        ':tracker:preBuild': UP_TO_DATE,
        ':tracker:preDebugBuild': UP_TO_DATE,
        ':tracker:prepareLintJarForPublish': SUCCESS,
        ':tracker:processDebugJavaRes': NO_SOURCE,
        ':tracker:processDebugManifest': FROM_CACHE,
        ':tracker:stripDebugDebugSymbols': FROM_CACHE,
        ':tracker:syncDebugLibJars': SUCCESS,
        ':wearable:assembleDebug': SUCCESS,
        ':wearable:checkDebugDuplicateClasses': FROM_CACHE,
        ':wearable:compileDebugAidl': NO_SOURCE,
        ':wearable:compileDebugJavaWithJavac': FROM_CACHE,
        ':wearable:compileDebugKotlin': FROM_CACHE,
        ':wearable:compileDebugRenderscript': NO_SOURCE,
        ':wearable:compileDebugShaders': FROM_CACHE,
        ':wearable:compileDebugSources': UP_TO_DATE,
        ':wearable:createDebugCompatibleScreenManifests': FROM_CACHE,
        ':wearable:dexBuilderDebug': FROM_CACHE,
        ':wearable:extractDeepLinksDebug': FROM_CACHE,
        ':wearable:generateDebugAssets': UP_TO_DATE,
        ':wearable:generateDebugBuildConfig': FROM_CACHE,
        ':wearable:generateDebugResValues': FROM_CACHE,
        ':wearable:generateDebugResources': UP_TO_DATE,
        ':wearable:javaPreCompileDebug': FROM_CACHE,
        ':wearable:kaptDebugKotlin': SUCCESS,
        ':wearable:kaptGenerateStubsDebugKotlin': FROM_CACHE,
        ':wearable:mainApkListPersistenceDebug': FROM_CACHE,
        ':wearable:mergeDebugAssets': FROM_CACHE,
        ':wearable:mergeDebugJavaResource': SUCCESS,
        ':wearable:mergeDebugJniLibFolders': FROM_CACHE,
        ':wearable:mergeDebugNativeLibs': FROM_CACHE,
        ':wearable:mergeDebugResources': FROM_CACHE,
        ':wearable:mergeDebugShaders': FROM_CACHE,
        ':wearable:mergeExtDexDebug': FROM_CACHE,
        ':wearable:mergeLibDexDebug': FROM_CACHE,
        ':wearable:mergeProjectDexDebug': FROM_CACHE,
        ':wearable:packageDebug': SUCCESS,
        ':wearable:preBuild': UP_TO_DATE,
        ':wearable:preDebugBuild': UP_TO_DATE,
        ':wearable:processDebugJavaRes': NO_SOURCE,
        ':wearable:processDebugManifest': FROM_CACHE,
        ':wearable:processDebugResources': FROM_CACHE,
        ':wearable:stripDebugDebugSymbols': FROM_CACHE,
        ':wearable:validateSigningDebug': FROM_CACHE,
    ]

    def cleanup() {
        // The daemons started by test kit need to be killed, so no locked files are left behind.
        DaemonLogsAnalyzer.newAnalyzer(homeDir.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }
}
