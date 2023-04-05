/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.buildinit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult

class JavaApplicationInitSoakTest extends AbstractIntegrationSpec {

    def "toolchain auto-provisioning works"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        def initDir = createDir('initDir')

        when:
        executer
            .inDirectory(initDir)
            .withTasks('init', '--type', 'java-application', '--dsl', 'kotlin')
            .run()

        and:
        def result = executer
            .inDirectory(initDir)
            .requireOwnGradleUserHomeDir()
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.auto-download=true")
            .withTasks('run')
            .run()

        then:
        result.assertOutputContains("Hello World!")
    }
}
