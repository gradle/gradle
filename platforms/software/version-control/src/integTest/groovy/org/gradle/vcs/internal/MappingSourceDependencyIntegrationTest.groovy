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


import org.gradle.vcs.git.GitVersionControlSpec

class MappingSourceDependencyIntegrationTest extends AbstractSourceDependencyIntegrationTest {
    @Override
    void mappingFor(String gitRepo, String coords, String repoDef) {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("${coords}") {
                        from(${GitVersionControlSpec.name}) {
                            url = uri("$gitRepo")
                            ${repoDef}
                        }
                    }
                }
            }
        """
    }

    def 'emits sensible error when bad code is in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        foo()
                    }
                }
            }
        """
        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasFileName("Settings file '$settingsFile.path'")
        failure.assertHasLineNumber(5)
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("No signature of method: org.gradle.vcs.internal.DefaultVcsMapping.foo() is applicable for argument types: () values: []")
    }

    def 'emits sensible error when bad module in vcsMappings block'() {
        settingsFile << """
            rootProject.name = 'test'
            sourceControl {
                vcsMappings {
                    withModule("broken") {
                        from(GitVersionControlSpec) {
                        }
                    }
                }
            }
        """

        expect:
        fails('assemble')
        failure.assertHasFileName("Settings file '$settingsFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasDescription("A problem occurred evaluating settings 'test'.")
        failure.assertHasCause("""Cannot convert the provided notation to a module identifier: broken.
The following types/formats are supported:
  - String describing the module in 'group:name' format, for example 'org.gradle:gradle-core'.""")
    }
}
