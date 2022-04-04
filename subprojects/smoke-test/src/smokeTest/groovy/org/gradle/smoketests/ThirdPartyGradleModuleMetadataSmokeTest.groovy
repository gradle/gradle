/*
 * Copyright 2016 the original author or authors.
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

import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.testkit.runner.BuildResult

class ThirdPartyGradleModuleMetadataSmokeTest extends AbstractSmokeTest {

    /**
     * Everything is done in one test to save execution time.
     * Running the producer build takes ~2min.
     */
    @ToBeFixedForConfigurationCache
    def 'produces expected metadata and can be consumed'() {
        given:
        BuildResult result
        useSample("gmm-example")
        def kotlinVersion = TestedVersions.kotlin.latestStartsWith("1.4")
        def androidPluginVersion = AGP_VERSIONS.getLatestOfMinor("4.2")
        def arch = OperatingSystem.current().macOsX ? 'MacosX64' : 'LinuxX64'

        def expectedMetadata = new File(testProjectDir, 'expected-metadata')
        def actualRepo = new File(testProjectDir, 'producer/repo')

        when:
        buildFile = new File(testProjectDir, "producer/${defaultBuildFileName}.kts")
        replaceVariablesInBuildFile(
            kotlinVersion: kotlinVersion,
            androidPluginVersion: androidPluginVersion)
        publish(kotlinVersion, androidPluginVersion)

        then:
        actualRepo.eachFileRecurse { actual ->
            def expected = new File(expectedMetadata, actual.name)
            if (expected.name.endsWith('.pom')) {
                assert expected.text == actual.text: "content mismatch: ${actual.name}]"
            }
            if (expected.name.endsWith('.module')) {
                compareJson(expected, actual)
            }
        }

        when:
        buildFile = new File(testProjectDir, "consumer/${defaultBuildFileName}.kts")
        replaceVariablesInBuildFile(
            kotlinVersion: kotlinVersion,
            androidPluginVersion: androidPluginVersion)
        result = consumer('java-app:run')

        then:
        trimmedOutput(result) == [
            'From java-library',
            'From kotlin-library',
            'From android-library',
            'From android-library (single variant)',
            'From android-kotlin-library',
            'From kotlin-multiplatform-library',
            'From kotlin-multiplatform-library (with Android variant)'
        ]

        when:
        result = consumer('kotlin-app:run')

        then:
        trimmedOutput(result) == [
            'From java-library',
            'From kotlin-library',
            'From android-library',
            'From android-library (single variant)',
            'From android-kotlin-library',
            'From kotlin-multiplatform-library',
            'From kotlin-multiplatform-library (with Android variant)'
        ]

        when:
        result = consumerWithJdk16WorkaroundForAndroidManifest('android-app:assembleFullDebug')

        then:
        trimmedOutput(result) == []

        when:
        result = consumerWithJdk16WorkaroundForAndroidManifest('android-kotlin-app:assembleFullDebug')

        then:
        trimmedOutput(result) == []

        when:
        result = consumer("native-app:runReleaseExecutable$arch")

        then:
        trimmedOutput(result) == [
            'From kotlin-multiplatform-library',
            'From kotlin-multiplatform-library (with Android variant)'
        ]
    }

    private static List<String> trimmedOutput(BuildResult result) {
        result.output.split('\n').findAll { !it.empty && !it.toLowerCase().contains('warning') }
    }

    private static SmokeTestGradleRunner setIllegalAccessPermitForJDK16KotlinCompilerDaemonOptions(SmokeTestGradleRunner runner) {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            // https://youtrack.jetbrains.com/issue/KT-44266#focus=Comments-27-4639508.0-0
            runner.withJvmArguments("-Dkotlin.daemon.jvm.options=" +
                "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED," +
                "--add-opens=java.base/java.util=ALL-UNNAMED")
        }
        return runner
    }

    private BuildResult publish(String kotlinVersion, String agpVersion) {
        return setIllegalAccessPermitForJDK16KotlinCompilerDaemonOptions(runner('publish'))
            .withProjectDir(new File(testProjectDir, 'producer'))
            .forwardOutput()
            .deprecations(KotlinMultiPlatformDeprecations) {
                expectKotlinJsCompileDestinationDirPropertyDeprecation(kotlinVersion)
                expectAndroidFileTreeForEmptySourcesDeprecationWarnings(agpVersion, "sourceFiles", "sourceDirs", "inputFiles", "projectNativeLibs")
            }.build()
    }

    private BuildResult consumer(String runTask) {
        setIllegalAccessPermitForJDK16KotlinCompilerDaemonOptions(runner(runTask, '-q'))
            .withProjectDir(new File(testProjectDir, 'consumer'))
            .forwardOutput()
            .build()
    }

    // Reevaluate if this is still needed when upgrading android plugin. Currently required with version 4.2.2
    private BuildResult consumerWithJdk16WorkaroundForAndroidManifest(String runTask) {
        def runner = runner(runTask, '-q')
            .withProjectDir(new File(testProjectDir, 'consumer'))
            .forwardOutput()
        if (JavaVersion.current().isJava9Compatible()) {
            runner.withJvmArguments("--add-opens", "java.base/java.io=ALL-UNNAMED")
        }
        return runner.build()
    }

    private static compareJson(File expected, File actual) {
        def actualJson = removeChangingDetails(new JsonSlurper().parseText(actual.text), actual.name)
        def expectedJson = removeChangingDetails(new JsonSlurper().parseText(expected.text), actual.name)
        assert actualJson.formatVersion == expectedJson.formatVersion
        assert actualJson.component == expectedJson.component: "component content mismatch: ${actual.name}"
        assert actualJson.variants as Set == expectedJson.variants as Set: "variants content mismatch: ${actual.name}"
    }

    private static removeChangingDetails(moduleRoot, String metadataFileName) {
        moduleRoot.variants.each { it.files.each { it.size = '' } }
        moduleRoot.variants.each { it.files.each { it.sha512 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha256 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha1 = '' } }
        moduleRoot.variants.each { it.files.each { it.md5 = '' } }

        if (metadataFileName.endsWith('-metadata-1.0.module')) {
            // bug in Kotlin metadata module publishing - wrong coordinates (ignored by Gradle)
            // https://youtrack.jetbrains.com/issue/KT-36494
            moduleRoot.component.module = ''
            moduleRoot.component.url = ''
        }

        moduleRoot
    }

    static class KotlinMultiPlatformDeprecations extends BaseDeprecations implements WithKotlinDeprecations, WithAndroidDeprecations {
        KotlinMultiPlatformDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }
    }
}
