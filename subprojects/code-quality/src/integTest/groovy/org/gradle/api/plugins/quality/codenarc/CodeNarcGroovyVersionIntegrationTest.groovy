/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.quality.codenarc

import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber

import static org.hamcrest.CoreMatchers.startsWith

class CodeNarcGroovyVersionIntegrationTest extends AbstractIntegrationSpec implements CodeNarcTestFixture {
    def setup() {
        writeRuleFile()
    }

    void writeBuildFile(String groovyVersion, String codenarcVersion) {
        String group = VersionNumber.parse(groovyVersion).major >= 4 ? "org.apache.groovy" : "org.codehaus.groovy"
        buildFile << """
            apply plugin: "groovy"
            apply plugin: "codenarc"

            ${mavenCentralRepository()}

            dependencies {
                implementation(platform("${group}:groovy-bom:${groovyVersion}"))
                implementation("${group}:groovy")
            }

            codenarc.toolVersion = "${codenarcVersion}"
        """.stripIndent()
    }

    def "analyze good code (groovy: #groovyVersion, codenarc: #codenarcVersion)"() {
        goodCode()
        writeBuildFile(groovyVersion, codenarcVersion)

        expect:
        succeeds("check")
        report("main").exists()
        report("test").exists()

        where:
        groovyVersion        | codenarcVersion
        latestGroovy3Version | CodeNarcPlugin.STABLE_VERSION
        latestGroovy4Version | CodeNarcPlugin.STABLE_VERSION_WITH_GROOVY4_SUPPORT
    }

    def "analyze bad code"() {
        badCode()
        writeBuildFile(groovyVersion, codenarcVersion)

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':codenarcTest'.")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found. See the report at:"))
        !report("main").text.contains("Class2")
        report("test").text.contains("testclass2")

        where:
        groovyVersion        | codenarcVersion
        latestGroovy3Version | CodeNarcPlugin.STABLE_VERSION
        latestGroovy4Version | CodeNarcPlugin.STABLE_VERSION_WITH_GROOVY4_SUPPORT
    }

    static String getLatestGroovy4Version() {
        return GroovyCoverage.SUPPORTED_BY_JDK.collect { VersionNumber.parse(it) }.findAll {it.major == 4 }.max()
    }

    static String getLatestGroovy3Version() {
        return GroovyCoverage.SUPPORTED_BY_JDK.collect { VersionNumber.parse(it) }.findAll {it.major == 3 }.max()
    }
}
