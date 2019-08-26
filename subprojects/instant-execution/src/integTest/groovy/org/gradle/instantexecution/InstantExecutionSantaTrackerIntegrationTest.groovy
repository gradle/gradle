/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import spock.lang.Unroll


/**
 * Integration test Santa Tracker android app against AGP nightly.
 */
class InstantExecutionSantaTrackerIntegrationTest extends AbstractInstantExecutionAndroidIntegrationTest {

    def setup() {
        executer.noDeprecationChecks()
        executer.withRepositoryMirrors()
    }

    @Unroll
    def "assembleDebug --dry-run on Santa Tracker #flavor"() {

        given:
        copyRemoteProject(remoteProject)
        withAgpNightly()

        when:
        instantRun ':santa-tracker:assembleDebug', '--dry-run', '--no-build-cache'

        then:
        instantRun ':santa-tracker:assembleDebug', '--dry-run', '--no-build-cache'

        where:
        flavor | remoteProject
        'Java' | "santaTrackerJava"
        // 'Kotlin' | "santaTrackerKotlin" // TODO:instant-execution Instant execution state could not be cached.
    }

    def "supported tasks up-to-date on Santa Tracker Java"() {

        given:
        copyRemoteProject("santaTrackerJava")
        withAgpNightly()

        and:
        def tasks = TASKS_TO_ASSEMBLE_DEBUG_JAVA + ['--no-build-cache'] - [
            // unsupported tasks
            ":santa-tracker:processDevelopmentDebugResources",
            ":santa-tracker:compileDevelopmentDebugJavaWithJavac",
            ":santa-tracker:compileDevelopmentDebugSources",
            ":santa-tracker:dexBuilderDevelopmentDebug",
            ":santa-tracker:mergeProjectDexDevelopmentDebug",
            ":santa-tracker:packageDevelopmentDebug",
            ":santa-tracker:assembleDevelopmentDebug",
            ":santa-tracker:assembleDebug",
        ]

        when:
        instantRun(*tasks)

        then:
        instantRun(*tasks)
    }

    def "supported tasks clean build on Santa Tracker Java"() {

        given:
        copyRemoteProject("santaTrackerJava")
        withAgpNightly()

        and:
        def libs = [':common', ':dasherdancer', ':doodles', ':presentquest', ':rocketsleigh', ':snowdown', ':village']
        def tasks = TASKS_TO_ASSEMBLE_DEBUG_JAVA + ['--no-build-cache'] - [
            // unsupported library tasks
            ':compileDebugLibraryResources',
            ':extractDeepLinksDebug',
            ':mergeDebugShaders',
            ':compileDebugShaders',
            ':generateDebugAssets',
            ':packageDebugAssets',
            ':processDebugJavaRes',
            ':bundleLibResDebug',
            ':bundleLibRuntimeDebug',
            ':createFullJarDebug',
            ':mergeDebugJniLibFolders',
            ':mergeDebugNativeLibs',
            ':stripDebugDebugSymbols',
            ':copyDebugJniLibsProjectOnly',
        ].collectMany { task -> libs.collect { lib -> "$lib$task" } } - [
            // unsupported application tasks
            ":santa-tracker:processDevelopmentDebugResources",
            ":santa-tracker:compileDevelopmentDebugJavaWithJavac",
            ":santa-tracker:compileDevelopmentDebugSources",
            ":santa-tracker:dexBuilderDevelopmentDebug",
            ":santa-tracker:mergeProjectDexDevelopmentDebug",
            ":santa-tracker:packageDevelopmentDebug",
            ":santa-tracker:assembleDevelopmentDebug",
            ":santa-tracker:assembleDebug",
        ]

        when:
        instantRun(*tasks)

        and:
        instantRun 'clean'

        then:
        instantRun(*tasks)
    }

    static final List<String> TASKS_TO_ASSEMBLE_DEBUG_JAVA = [
        ':common:preBuild',
        ':common:preDebugBuild',
        ':common:compileDebugAidl',
        ':common:compileDebugRenderscript',
        ':common:checkDebugManifest',
        ':common:generateDebugBuildConfig',
        ':common:generateDebugResValues',
        ':common:generateDebugResources',
        ':common:packageDebugResources',
        ':common:parseDebugLocalResources',
        ':common:processDebugManifest',
        ':common:generateDebugRFile',
        ':common:javaPreCompileDebug',
        ':common:compileDebugJavaWithJavac',
        ':common:bundleLibCompileDebug',
        ':dasherdancer:preBuild',
        ':dasherdancer:preDebugBuild',
        ':dasherdancer:compileDebugAidl',
        ':common:packageDebugRenderscript',
        ':dasherdancer:compileDebugRenderscript',
        ':dasherdancer:checkDebugManifest',
        ':dasherdancer:generateDebugBuildConfig',
        ':dasherdancer:generateDebugResValues',
        ':dasherdancer:generateDebugResources',
        ':dasherdancer:packageDebugResources',
        ':dasherdancer:parseDebugLocalResources',
        ':dasherdancer:processDebugManifest',
        ':dasherdancer:generateDebugRFile',
        ':dasherdancer:javaPreCompileDebug',
        ':dasherdancer:compileDebugJavaWithJavac',
        ':dasherdancer:bundleLibCompileDebug',
        ':doodles:preBuild',
        ':doodles:preDebugBuild',
        ':doodles:compileDebugAidl',
        ':doodles:compileDebugRenderscript',
        ':doodles:checkDebugManifest',
        ':doodles:generateDebugBuildConfig',
        ':doodles:generateDebugResValues',
        ':doodles:generateDebugResources',
        ':doodles:packageDebugResources',
        ':doodles:parseDebugLocalResources',
        ':doodles:processDebugManifest',
        ':doodles:generateDebugRFile',
        ':doodles:javaPreCompileDebug',
        ':doodles:compileDebugJavaWithJavac',
        ':doodles:bundleLibCompileDebug',
        ':presentquest:preBuild',
        ':presentquest:preDebugBuild',
        ':presentquest:compileDebugAidl',
        ':presentquest:compileDebugRenderscript',
        ':presentquest:checkDebugManifest',
        ':presentquest:generateDebugBuildConfig',
        ':presentquest:generateDebugResValues',
        ':presentquest:generateDebugResources',
        ':presentquest:packageDebugResources',
        ':presentquest:parseDebugLocalResources',
        ':presentquest:processDebugManifest',
        ':presentquest:generateDebugRFile',
        ':presentquest:javaPreCompileDebug',
        ':presentquest:compileDebugJavaWithJavac',
        ':presentquest:bundleLibCompileDebug',
        ':rocketsleigh:preBuild',
        ':rocketsleigh:preDebugBuild',
        ':rocketsleigh:compileDebugAidl',
        ':rocketsleigh:compileDebugRenderscript',
        ':rocketsleigh:checkDebugManifest',
        ':rocketsleigh:generateDebugBuildConfig',
        ':rocketsleigh:generateDebugResValues',
        ':rocketsleigh:generateDebugResources',
        ':rocketsleigh:packageDebugResources',
        ':rocketsleigh:parseDebugLocalResources',
        ':rocketsleigh:processDebugManifest',
        ':rocketsleigh:generateDebugRFile',
        ':rocketsleigh:javaPreCompileDebug',
        ':rocketsleigh:compileDebugJavaWithJavac',
        ':rocketsleigh:bundleLibCompileDebug',
        ':santa-tracker:preBuild',
        ':santa-tracker:preDevelopmentDebugBuild',
        ':village:preBuild',
        ':village:preDebugBuild',
        ':village:compileDebugAidl',
        ':santa-tracker:compileDevelopmentDebugAidl',
        ':dasherdancer:packageDebugRenderscript',
        ':doodles:packageDebugRenderscript',
        ':presentquest:packageDebugRenderscript',
        ':rocketsleigh:packageDebugRenderscript',
        ':village:packageDebugRenderscript',
        ':santa-tracker:compileDevelopmentDebugRenderscript',
        ':santa-tracker:checkDevelopmentDebugManifest',
        ':santa-tracker:generateDevelopmentDebugBuildConfig',
        ':village:compileDebugRenderscript',
        ':village:checkDebugManifest',
        ':village:generateDebugBuildConfig',
        ':village:generateDebugResValues',
        ':village:generateDebugResources',
        ':village:packageDebugResources',
        ':village:parseDebugLocalResources',
        ':village:processDebugManifest',
        ':village:generateDebugRFile',
        ':village:javaPreCompileDebug',
        ':village:compileDebugJavaWithJavac',
        ':village:bundleLibCompileDebug',
        ':santa-tracker:javaPreCompileDevelopmentDebug',
        ':common:compileDebugLibraryResources',
        ':dasherdancer:compileDebugLibraryResources',
        ':doodles:compileDebugLibraryResources',
        ':presentquest:compileDebugLibraryResources',
        ':rocketsleigh:compileDebugLibraryResources',
        ':santa-tracker:mainApkListPersistenceDevelopmentDebug',
        ':santa-tracker:generateDevelopmentDebugResValues',
        ':santa-tracker:generateDevelopmentDebugResources',
        ':santa-tracker:mergeDevelopmentDebugResources',
        ':common:extractDeepLinksDebug',
        ':dasherdancer:extractDeepLinksDebug',
        ':doodles:extractDeepLinksDebug',
        ':presentquest:extractDeepLinksDebug',
        ':rocketsleigh:extractDeepLinksDebug',
        ':santa-tracker:createDevelopmentDebugCompatibleScreenManifests',
        ':santa-tracker:extractDeepLinksDevelopmentDebug',
        ':village:extractDeepLinksDebug',
        ':santa-tracker:processDevelopmentDebugManifest',
        ':village:compileDebugLibraryResources',
        ':santa-tracker:processDevelopmentDebugResources',
        ':santa-tracker:compileDevelopmentDebugJavaWithJavac',
        ':santa-tracker:compileDevelopmentDebugSources',
        ':common:mergeDebugShaders',
        ':common:compileDebugShaders',
        ':common:generateDebugAssets',
        ':common:packageDebugAssets',
        ':dasherdancer:mergeDebugShaders',
        ':dasherdancer:compileDebugShaders',
        ':dasherdancer:generateDebugAssets',
        ':dasherdancer:packageDebugAssets',
        ':doodles:mergeDebugShaders',
        ':doodles:compileDebugShaders',
        ':doodles:generateDebugAssets',
        ':doodles:packageDebugAssets',
        ':presentquest:mergeDebugShaders',
        ':presentquest:compileDebugShaders',
        ':presentquest:generateDebugAssets',
        ':presentquest:packageDebugAssets',
        ':rocketsleigh:mergeDebugShaders',
        ':rocketsleigh:compileDebugShaders',
        ':rocketsleigh:generateDebugAssets',
        ':rocketsleigh:packageDebugAssets',
        ':santa-tracker:mergeDevelopmentDebugShaders',
        ':santa-tracker:compileDevelopmentDebugShaders',
        ':santa-tracker:generateDevelopmentDebugAssets',
        ':village:mergeDebugShaders',
        ':village:compileDebugShaders',
        ':village:generateDebugAssets',
        ':village:packageDebugAssets',
        ':santa-tracker:mergeDevelopmentDebugAssets',
        ':common:processDebugJavaRes',
        ':common:bundleLibResDebug',
        ':dasherdancer:processDebugJavaRes',
        ':dasherdancer:bundleLibResDebug',
        ':doodles:processDebugJavaRes',
        ':doodles:bundleLibResDebug',
        ':presentquest:processDebugJavaRes',
        ':presentquest:bundleLibResDebug',
        ':rocketsleigh:processDebugJavaRes',
        ':rocketsleigh:bundleLibResDebug',
        ':santa-tracker:processDevelopmentDebugJavaRes',
        ':village:processDebugJavaRes',
        ':village:bundleLibResDebug',
        ':santa-tracker:mergeDevelopmentDebugJavaResource',
        ':santa-tracker:checkDevelopmentDebugDuplicateClasses',
        ':santa-tracker:mergeExtDexDevelopmentDebug',
        ':village:bundleLibRuntimeDebug',
        ':village:createFullJarDebug',
        ':dasherdancer:bundleLibRuntimeDebug',
        ':dasherdancer:createFullJarDebug',
        ':rocketsleigh:bundleLibRuntimeDebug',
        ':rocketsleigh:createFullJarDebug',
        ':doodles:bundleLibRuntimeDebug',
        ':doodles:createFullJarDebug',
        ':presentquest:bundleLibRuntimeDebug',
        ':presentquest:createFullJarDebug',
        ':common:bundleLibRuntimeDebug',
        ':common:createFullJarDebug',
        ':santa-tracker:mergeLibDexDevelopmentDebug',
        ':santa-tracker:dexBuilderDevelopmentDebug',
        ':santa-tracker:mergeProjectDexDevelopmentDebug',
        ':santa-tracker:validateSigningDevelopmentDebug',
        ':santa-tracker:signingConfigWriterDevelopmentDebug',
        ':common:mergeDebugJniLibFolders',
        ':common:mergeDebugNativeLibs',
        ':common:stripDebugDebugSymbols',
        ':common:copyDebugJniLibsProjectOnly',
        ':dasherdancer:mergeDebugJniLibFolders',
        ':dasherdancer:mergeDebugNativeLibs',
        ':dasherdancer:stripDebugDebugSymbols',
        ':dasherdancer:copyDebugJniLibsProjectOnly',
        ':doodles:mergeDebugJniLibFolders',
        ':doodles:mergeDebugNativeLibs',
        ':doodles:stripDebugDebugSymbols',
        ':doodles:copyDebugJniLibsProjectOnly',
        ':presentquest:mergeDebugJniLibFolders',
        ':presentquest:mergeDebugNativeLibs',
        ':presentquest:stripDebugDebugSymbols',
        ':presentquest:copyDebugJniLibsProjectOnly',
        ':rocketsleigh:mergeDebugJniLibFolders',
        ':rocketsleigh:mergeDebugNativeLibs',
        ':rocketsleigh:stripDebugDebugSymbols',
        ':rocketsleigh:copyDebugJniLibsProjectOnly',
        ':santa-tracker:mergeDevelopmentDebugJniLibFolders',
        ':village:mergeDebugJniLibFolders',
        ':village:mergeDebugNativeLibs',
        ':village:stripDebugDebugSymbols',
        ':village:copyDebugJniLibsProjectOnly',
        ':santa-tracker:mergeDevelopmentDebugNativeLibs',
        ':santa-tracker:stripDevelopmentDebugDebugSymbols',
        ':santa-tracker:packageDevelopmentDebug',
        ':santa-tracker:assembleDevelopmentDebug',
        ':santa-tracker:assembleDebug',
    ]
}
