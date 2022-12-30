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
    private static final STABLE_VERSION = CodeNarcPlugin.DEFAULT_CODENARC_VERSION
    private static final STABLE_VERSION_WITH_GROOVY4_SUPPORT = "${STABLE_VERSION}-groovy-4.0"

    def setup() {
        buildFile << """
            apply plugin: "groovy"
            apply plugin: "codenarc"

            ${mavenCentralRepository()}

            dependencies {
                implementation(platform("org.apache.groovy:groovy-bom:${latestGroovy4Version}"))
                implementation("org.apache.groovy:groovy")

                codenarc("org.codenarc:CodeNarc:${STABLE_VERSION_WITH_GROOVY4_SUPPORT}")
                codenarc(platform("org.apache.groovy:groovy-bom:${latestGroovy4Version}"))
            }
        """.stripIndent()

        writeRuleFile()
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds("check")
        report("main").exists()
        report("test").exists()
    }

    def "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':codenarcTest'.")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found. See the report at:"))
        !report("main").text.contains("Class2")
        report("test").text.contains("testclass2")
    }

    def getLatestGroovy4Version() {
        return GroovyCoverage.SUPPORTED_BY_JDK.collect { VersionNumber.parse(it) }.findAll {it.major == 4 }.max()
    }
}
