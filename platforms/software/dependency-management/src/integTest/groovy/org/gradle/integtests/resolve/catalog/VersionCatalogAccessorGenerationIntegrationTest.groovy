/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.test.fixtures.file.TestFile

class VersionCatalogAccessorGenerationIntegrationTest extends AbstractVersionCatalogIntegrationTest {

    def setup() {
        enableProblemsApiCheck()
    }

    TestFile tomlFile = testDirectory.file("gradle/libs.versions.toml")

    @VersionCatalogProblemTestFor(VersionCatalogProblemId.ACCESSOR_NAME_CLASH)
    def "reasonable error message if library aliases clash"() {
        tomlFile << """[libraries]
groovy-json = "org.groovy:json:1.0"
groovyJson = "org.groovy:json:2.0"
"""

        when:
        fails 'help'

        then:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:accessor-name-clash'
            definition.id.displayName == 'Accessor name clash'
            contextualLabel == 'In version catalog libs, library aliases groovy.json and groovyJson are mapped to the same accessor name getGroovyJson()'
            details == 'A name clash was detected'
            definition.documentationLink.url == docUrlFor('accessor_name_clash')
            solutions == ['Use a different alias for groovy.json and groovyJson']
        }
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.ACCESSOR_NAME_CLASH
    )
    def "reasonable error message if bundle aliases clash"() {
        tomlFile << """[libraries]
foo = "org:foo:1.0"
bar = "org:bar:1.0"

[bundles]
one-cool = ["foo", "bar"]
oneCool = ["foo", "bar"]
"""

        when:
        fails 'help'

        then:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:accessor-name-clash'
            definition.id.displayName == 'Accessor name clash'
            contextualLabel == 'In version catalog libs, dependency bundles one.cool and oneCool are mapped to the same accessor name getOneCoolBundle()'
            details == 'A name clash was detected'
            definition.documentationLink.url == docUrlFor('accessor_name_clash')
            solutions == ['Use a different alias for one.cool and oneCool']
        }
    }
}
