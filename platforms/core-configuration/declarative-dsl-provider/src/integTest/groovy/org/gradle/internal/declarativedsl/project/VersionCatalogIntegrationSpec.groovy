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

package org.gradle.internal.declarativedsl.project

import org.gradle.features.internal.ProjectTypeFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.containsString

class VersionCatalogIntegrationSpec extends AbstractIntegrationSpec implements ProjectTypeFixture {

    def 'setup'() {
        file("gradle/libs.versions.toml") << """
            [libraries]
            bar = { module = "foo:bar", version = "1.0" }
        """

        withProjectTypeDefinitionWithDependencies().prepareToExecute()
        file("settings.gradle.dcl") << pluginsFromIncludedBuild
    }

    def 'version catalogs appear in the DSL'() {
        when:
        file("build.gradle.dcl") << """
            testProjectType {
                dependencies {
                    implementation(catalogs.libs.bar())
                }
            }
        """

        then:
        succeeds()
    }

    def 'non-existent version catalog reference gives meaningful error'() {
        when:
        file("build.gradle.dcl") << """
            testProjectType {
                dependencies {
                    implementation(catalogs.nonExistent.lib())
                }
            }
        """

        then:
        fails().assertThatCause(containsString("4:45: unresolved reference 'nonExistent'"))
    }

    def 'non-existent library reference gives meaningful error'() {
        when:
        file("build.gradle.dcl") << """
            testProjectType {
                dependencies {
                    implementation(catalogs.libs.nonExistent())
                }
            }
        """

        then:
        fails().assertThatCause(containsString("4:50: unresolved function call signature for 'nonExistent'"))
    }
}
