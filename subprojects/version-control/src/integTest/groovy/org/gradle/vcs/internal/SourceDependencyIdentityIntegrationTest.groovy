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


class SourceDependencyIdentityIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())

    def setup() {
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
            rootProject.name = 'buildB'
        """
        repo.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.2'
        """
    }

    def "includes build identifier in error message on failure to resolve dependencies of build"() {
        repo.file("build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':buildB:compileJava'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':buildB:compileClasspath'.")
        // TODO - incorrect project path
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :dep""")
    }

    def "includes build identifier in task failure error message"() {
        repo.file("build.gradle") << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':buildB:classes'.")
        failure.assertHasCause("broken")
    }

    def "includes build identifier in dependency resolution results"() {
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")

        buildFile << """
            classes.doLast {
                def components = configurations.compileClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 2
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                // TODO - should be 'buildA'
                assert components[0].projectName == ':'
                assert components[1].build.name == 'buildB'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == 'buildB'
            }
        """

        expect:
        succeeds( ":assemble")
    }
}
