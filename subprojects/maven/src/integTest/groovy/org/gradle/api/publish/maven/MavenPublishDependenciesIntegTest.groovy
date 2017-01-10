/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class MavenPublishDependenciesIntegTest extends AbstractIntegrationSpec {

    public void "version range is mapped to maven syntax in published pom file"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile "group:projectA:latest.release"
                runtime "group:projectB:latest.integration"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
"""

        when:
        succeeds "publish"

        then:
        repoModule.assertPublishedAsJavaModule()
        repoModule.parsedPom.scopes.runtime.expectDependency('group:projectA:RELEASE')
        repoModule.parsedPom.scopes.runtime.expectDependency('group:projectB:LATEST')
    }

    @Issue("GRADLE-3233")
    @Unroll
    def "publishes POM dependency with #versionType version for Gradle dependency with null version"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile $dependencyNotation
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        repoModule.assertPublishedAsJavaModule()
        repoModule.parsedPom.scopes.runtime.assertDependsOn("group:projectA:")
        def dependency = repoModule.parsedPom.scopes.runtime.dependencies.get("group:projectA:")
        dependency.groupId == "group"
        dependency.artifactId == "projectA"
        dependency.version == ""

        where:
        versionType | dependencyNotation
        "empty"     | "'group:projectA'"
        "null"      | "group:'group', name:'projectA', version:null"
    }
}
