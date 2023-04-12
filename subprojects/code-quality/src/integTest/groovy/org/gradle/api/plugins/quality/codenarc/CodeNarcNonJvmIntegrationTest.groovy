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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.quality.integtest.fixtures.CodeNarcCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

@TargetCoverage({ CodeNarcCoverage.supportedVersionsByCurrentJdk })
@Requires(TestPrecondition.STABLE_GROOVY)
class CodeNarcNonJvmIntegrationTest extends MultiVersionIntegrationSpec implements CodeNarcTestFixture {

    @Issue("https://github.com/gradle/gradle/issues/23343")
    def "can apply codenarc plugin to a non-jvm project"() {
        buildFile << """
            plugins {
                id 'base'
                id 'codenarc'
            }

            ${mavenCentralRepository()}

            codenarc {
                toolVersion = '${version}'
            }

            tasks.register("codenarcGradle", CodeNarc) {
                source "test.groovy"
            }

            ${JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14) ?
                """
                    configurations.codenarc {
                        resolutionStrategy.force 'org.codehaus.groovy:groovy:${GroovyCoverage.MINIMAL_GROOVY_3}' // force latest Groovy 3 when using Java 14+.  Do not use GroovySystem#version as Groovy 4 needs different coordinates
                    }
                """ : ""}
        """
        writeRuleFile()
        file('test.groovy').createFile()

        expect:
        succeeds("codenarcGradle")
    }
}
