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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.internal.os.OperatingSystem
import org.gradle.testkit.runner.BuildResult

class ThirdPartyGradleModuleMetadataSmokeTest extends AbstractSmokeTest {

    /**
     * Everything is done in one test to safe execution time.
     * Running the producer build takes ~2min.
     */
    @ToBeFixedForInstantExecution
    def 'produces expected metadata and can be consumed'() {
        given:
        BuildResult result
        useSample("gmm-example")
        def kotlinVersion = TestedVersions.kotlin.versions.last()
        def androidPluginVersion = AGP_VERSIONS.getLatestOfMinor("4.0")
        def arch = OperatingSystem.current().macOsX ? 'MacosX64' : 'LinuxX64'

        def expectedMetadata = new File(testProjectDir.root, 'expected-metadata')
        def actualRepo = new File(testProjectDir.root, 'producer/repo')

        when:
        buildFile = new File(testProjectDir.root, "producer/${defaultBuildFileName}.kts")
        replaceVariablesInBuildFile(
            kotlinVersion: kotlinVersion,
            androidPluginVersion: androidPluginVersion)
        publish()

        then:
        actualRepo.eachFileRecurse { actual ->
            def expected = new File(expectedMetadata, actual.name)
            if (expected.name.endsWith('.pom')) {
                assert expected.text == actual.text : "content mismatch: ${actual.name}]"
            }
            if (expected.name.endsWith('.module')) {
                compareJson(expected, actual)
            }
        }

        when:
        buildFile = new File(testProjectDir.root, "consumer/${defaultBuildFileName}.kts")
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
        result = consumer('android-app:assembleFullDebug')

        then:
        trimmedOutput(result) == []

        when:
        result = consumer('android-kotlin-app:assembleFullDebug')

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

    private List<String> trimmedOutput(BuildResult result) {
        result.output.split('\n').findAll { !it.empty && !it.contains('warning') }
    }

    private BuildResult publish() {
        runner('publish').withProjectDir(
            new File(testProjectDir.root, 'producer')).forwardOutput().build()
    }

    private BuildResult consumer(String runTask) {
        runner(runTask, '-q').withProjectDir(
            new File(testProjectDir.root, 'consumer')).forwardOutput().build()
    }


    private static compareJson(File expected, File actual) {
        def actualJson = removeChangingDetails(new JsonSlurper().parseText(actual.text), actual.name)
        def expectedJson = removeChangingDetails(new JsonSlurper().parseText(expected.text), actual.name)
        assert actualJson.formatVersion == expectedJson.formatVersion
        assert actualJson.component == expectedJson.component : "component content mismatch: ${actual.name}"
        assert actualJson.variants as Set == expectedJson.variants as Set : "variants content mismatch: ${actual.name}"
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
}
