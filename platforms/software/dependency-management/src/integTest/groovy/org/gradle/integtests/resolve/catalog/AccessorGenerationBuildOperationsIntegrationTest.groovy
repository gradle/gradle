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

package org.gradle.integtests.resolve.catalog

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39539")
class AccessorGenerationBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        // The generated accessors are kept in the Gradle user home, and are not generated again when found there
        executer.requireOwnGradleUserHomeDir()
    }

    def "generates the dependency accessors of a catalog in a build operation"() {
        given:
        file("gradle/libs.versions.toml") << """
            [libraries]
            foo = "org:foo:1.0"
        """

        when:
        succeeds 'help'

        then:
        def generate = operations.only("Generate dependency accessors for 'libs'")
        operations.search(generate, { it.displayName == "Executing generation of dependency accessors for libs" }).size() == 1
    }

    def "generates the project accessors in a build operation"() {
        given:
        settingsFile << """
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            rootProject.name = "root"
            include "lib"
        """
        file("lib/build.gradle") << ""

        when:
        succeeds 'help'

        then:
        def generate = operations.only("Generate project accessors")
        operations.search(generate, { it.displayName == "Executing generation of project accessors" }).size() == 1
    }

    def "adds the path of an included build to its operations"() {
        given:
        settingsFile << "includeBuild 'included'"
        file("included/settings.gradle") << "rootProject.name = 'included'"
        file("included/gradle/libs.versions.toml") << """
            [libraries]
            foo = "org:foo:1.0"
        """
        file("included/build.gradle") << "tasks.register('work')"
        buildFile << """
            tasks.register('work') {
                dependsOn gradle.includedBuild('included').task(':work')
            }
        """

        when:
        succeeds 'work'

        then:
        operations.only("Generate dependency accessors for 'libs' (:included)")
    }
}
