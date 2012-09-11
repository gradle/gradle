/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.github

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GitHubDependenciesPluginIntegrationTest extends AbstractIntegrationSpec {

    // Note: this is hitting the real GitHub, so will fail if GitHub is down.
    def "can resolve from github"() {
        given:
        buildFile << """
            apply plugin: 'github-dependencies'

            repositories {
                github.downloads {
                    user "gradleware"
                }
            }

            configurations { deps }

            dependencies {
                deps "gradle-talks:gradle-migration@zip"
            }

            task getdeps(type: Copy) {
                from configurations.deps
                into "deps"
            }
        """

        when:
        executer.withArguments("-i", "--refresh-dependencies")
        run "getdeps"

        then:
        file("deps/gradle-migration-.zip").exists()
    }

}
