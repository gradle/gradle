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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber

import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.startsWith
import static org.junit.Assume.assumeThat

class CodeNarcGroovyVersionIntegrationTest extends AbstractIntegrationSpec implements CodeNarcTestFixture {
    static final String STABLE_VERSION = "3.2.0"
    static final String STABLE_VERSION_WITH_GROOVY4_SUPPORT = "${STABLE_VERSION}-groovy-4.0"

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

            testing.suites.test.useJUnit()
            codenarc.toolVersion = "${codenarcVersion}"
        """.stripIndent()
    }

    def "analyze good code (groovy: #groovyMajorVersion, codenarc: #codenarcVersion)"() {
        def groovyVersion = getLatestGroovyVersion(groovyMajorVersion)
        assumeThat("Groovy $groovyMajorVersion is not supported on this JVM", groovyVersion, not(null))
        goodCode()
        writeBuildFile(groovyVersion, codenarcVersion)

        expect:
        succeeds("check")
        report("main").exists()
        report("test").exists()

        where:
        groovyMajorVersion | codenarcVersion
        3                  | STABLE_VERSION
        4                  | STABLE_VERSION_WITH_GROOVY4_SUPPORT
    }

    def "analyze bad code (groovy: #groovyMajorVersion, codenarc: #codenarcVersion)"() {
        def groovyVersion = getLatestGroovyVersion(groovyMajorVersion)
        assumeThat("Groovy $groovyMajorVersion is not supported on this JVM", groovyVersion, not(null))
        badCode()
        writeBuildFile(groovyVersion, codenarcVersion)

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':codenarcTest' (registered by plugin 'org.gradle.codenarc').")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found. See the report at:"))
        !report("main").text.contains("Class2")
        report("test").text.contains("testclass2")

        where:
        groovyMajorVersion | codenarcVersion
        3                  | STABLE_VERSION
        4                  | STABLE_VERSION_WITH_GROOVY4_SUPPORT
    }

    static String getLatestGroovyVersion(int majorVersion) {
        return GroovyCoverage.SUPPORTED_BY_JDK
            .collect { VersionNumber.parse(it) }
            .findAll { it.major == majorVersion }
            .max()
    }
}
