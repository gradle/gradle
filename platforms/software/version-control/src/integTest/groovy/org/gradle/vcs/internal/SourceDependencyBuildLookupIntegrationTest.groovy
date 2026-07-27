/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

class SourceDependencyBuildLookupIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('buildB', testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = "root"
            sourceControl {
                vcsMappings {
                    withModule("org.test:buildB") { details ->
                        from(GitVersionControlSpec) {
                            url = uri('$repo.url')
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java'
            dependencies { implementation 'org.test:buildB:2.0' }
        """

        repo.file("settings.gradle") << """
            rootProject.name = 'buildB'
        """
        repo.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
        """
        repo.commit("version 2")
        repo.createLightWeightTag("2.0")
    }

    def "source dependency builds are not visible to main build"() {
        given:
        buildFile << """
            // A source-dependency build participates in resolution (its jar is built below) but must
            // never be exposed as an included build. Assert this at configuration time rather than
            // reading `gradle` from a task action at execution time, which is configuration-cache safe.
            assert gradle.includedBuilds.empty
            try {
                gradle.includedBuild("buildB")
                throw new IllegalStateException("Expected source dependency build 'buildB' to be invisible as an included build")
            } catch (org.gradle.api.UnknownDomainObjectException e) {
                assert e.message == "Included build 'buildB' not found in build ':'."
            }

            task checkBuildB {
                dependsOn classes
            }
        """

        when:
        succeeds("checkBuildB")

        then:
        result.assertTaskScheduled(":buildB:jar")
    }
}
