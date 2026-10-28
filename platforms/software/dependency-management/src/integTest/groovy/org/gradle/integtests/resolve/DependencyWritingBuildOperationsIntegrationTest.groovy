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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39528")
class DependencyWritingBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        mavenRepo.module('org', 'foo', '1.0').publish()
    }

    def "writes the dependency locks of each owner in a build operation"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"
        file('a/build.gradle') << resolvingProject()
        file('b/build.gradle') << resolvingProject()
        file('c/build.gradle') << ""

        when:
        succeeds 'resolve', '--write-locks'

        then:
        operations.only("Write dependency locks for project ':a'")
        operations.only("Write dependency locks for project ':b'")
        operations.none(~/Write dependency locks for project ':c'/)
        operations.none(~/Write dependency locks for root project .*/)
    }

    def "writes the verification metadata in a build operation, with the resolution under it"() {
        given:
        buildFile << resolvingProject()

        when:
        succeeds 'resolve', '--write-verification-metadata', 'sha256'

        then:
        def writing = operations.only("Write dependency verification metadata")
        !operations.search(writing, { it.displayName.startsWith("Resolving configurations of") }).empty
    }

    def "does not write the verification metadata in a build operation without the option"() {
        given:
        buildFile << resolvingProject()

        when:
        succeeds 'resolve'

        then:
        operations.none(~/Write dependency verification metadata.*/)
    }

    def "adds the path of an included build to its verification metadata operation"() {
        given:
        settingsFile << "includeBuild 'included'"
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << resolvingProject()
        buildFile << """
            tasks.register('resolve') {
                dependsOn gradle.includedBuild('included').task(':resolve')
            }
        """

        when:
        succeeds 'resolve', '--write-verification-metadata', 'sha256'

        then:
        operations.only("Write dependency verification metadata (:included)")
    }

    def "does not add the path of an included build to its dependency lock operations, as their owners name the build"() {
        given:
        settingsFile << "includeBuild 'included'"
        file('included/settings.gradle') << lockedBuildscript() + """
            rootProject.name = 'included'
            include 'sub'
        """
        file('included/build.gradle') << resolvingProject()
        file('included/sub/build.gradle') << lockedBuildscript() + resolvingProject()
        buildFile << """
            tasks.register('resolve') {
                dependsOn gradle.includedBuild('included').task(':resolve')
                dependsOn gradle.includedBuild('included').task(':sub:resolve')
            }
        """

        when:
        succeeds 'resolve', '--write-locks'

        then:
        operations.only("Write dependency locks for settings file 'included${File.separator}settings.gradle'")
        operations.only("Write dependency locks for project ':included'")
        operations.only("Write dependency locks for project ':included:sub'")
        operations.only("Write dependency locks for buildscript of project ':included:sub'")
    }

    private String lockedBuildscript() {
        """
            buildscript {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                configurations.classpath {
                    resolutionStrategy.activateDependencyLocking()
                }
                dependencies {
                    classpath 'org:foo:1.0'
                }
            }
        """
    }

    private String resolvingProject() {
        """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.activateDependencyLocking()
                }
            }
            dependencies {
                conf 'org:foo:1.0'
            }
            tasks.register('resolve') {
                def files = configurations.conf
                doLast {
                    files.files
                }
            }
        """
    }
}
