/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Smoke coverage for the {@code .gradle.xdcl} scripting language: proves the distribution under
 * test routes xdcl settings/build scripts natively and that script failures surface through the
 * Problems API with file:line:column coordinates. Broader functional coverage builds on this.
 */
class XdclScriptingSmokeIntegrationTest extends AbstractIntegrationSpec {

    def "routes settings.gradle.xdcl and binds include"() {
        given:
        file("app").createDir()
        file("settings.gradle.xdcl") << '''settings {
            |  include ["app"]
            |}
            |'''.stripMargin()

        when:
        succeeds("projects")

        then:
        outputContains("Project ':app'")
    }

    def "an evaluation error fails the build as a located problem"() {
        given:
        enableProblemsApiCheck()
        file("settings.gradle.xdcl") << '''settings {
            |  includ ["app"]
            |}
            |'''.stripMargin()

        when:
        fails("help")

        then:
        failure.assertHasDescription("${testDirectory.file('settings.gradle.xdcl')}:2:3")

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("includ")
        }
    }
}
