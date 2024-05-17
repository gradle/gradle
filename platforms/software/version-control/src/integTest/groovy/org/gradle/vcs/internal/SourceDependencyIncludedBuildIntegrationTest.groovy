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


class SourceDependencyIncludedBuildIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('buildB', temporaryFolder.getTestDirectory())

    def "source dependency cannot (yet) define any included builds"() {
        settingsFile << """
            rootProject.name = 'buildA'
            sourceControl {
                vcsMappings {
                    withModule("org.test:buildB") {
                        from(GitVersionControlSpec) {
                            url = uri("${repo.url}")
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            dependencies { implementation 'org.test:buildB:1.2' }
        """

        repo.file("settings.gradle") << """
            includeBuild 'child'
        """
        repo.file("child/settings.gradle").createFile()
        repo.commit("version 1.2")
        repo.createLightWeightTag("1.2")

        when:
        fails("assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Cannot include build 'child' in build ':buildB'. This is not supported yet.")
    }
}
