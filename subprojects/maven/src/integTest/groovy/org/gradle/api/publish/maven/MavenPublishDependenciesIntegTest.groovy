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

    void "version range is mapped to maven syntax in published pom file"() {
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
        repoModule.parsedPom.scopes.compile.expectDependency('group:projectA:RELEASE')
        repoModule.parsedPom.scopes.compile.expectDependency('group:projectB:LATEST')
    }

    @Issue('GRADLE-1574')
    def "publishes wildcard exclusions for a non-transitive dependency"() {
        given:
        def module = mavenRepo.module('group', 'root', '1.0')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile ('commons-collections:commons-collections:3.2.2') { transitive = false }
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

        then: "wildcard exclusions are applied to the dependency"
        def pom = module.parsedPom
        def exclusions = pom.scopes.compile.dependencies['commons-collections:commons-collections:3.2.2'].exclusions
        exclusions.size() == 1 && exclusions[0].groupId=='*' && exclusions[0].artifactId=='*'
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
        repoModule.parsedPom.scopes.compile.assertDependsOn("group:projectA:")
        def dependency = repoModule.parsedPom.scopes.compile.dependencies.get("group:projectA:")
        dependency.groupId == "group"
        dependency.artifactId == "projectA"
        dependency.version == ""

        where:
        versionType | dependencyNotation
        "empty"     | "'group:projectA'"
        "null"      | "group:'group', name:'projectA', version:null"
    }

    void "defaultDependencies are included in published pom file"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: "java"
            apply plugin: "maven-publish"

            group = 'group'
            version = '1.0'

            configurations.compile.defaultDependencies { deps ->
                deps.add project.dependencies.create("org:default-dependency:1.0")
            }
            configurations.implementation.defaultDependencies { deps ->
                deps.add project.dependencies.create("org:default-dependency:1.0")
            }
            dependencies {
                implementation "org:explicit-dependency:1.0"
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
        repoModule.parsedPom.scopes.compile?.expectDependency('org:default-dependency:1.0')
        repoModule.parsedPom.scopes.runtime?.expectDependency('org:explicit-dependency:1.0')
    }

    void "dependency mutations are reflected in published pom file"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: "java"
            apply plugin: "maven-publish"

            group = 'group'
            version = '1.0'

            dependencies {
                compile "org.test:dep1:1.0"
            }
            configurations.compile.withDependencies { deps ->
                deps.each { dep ->
                    dep.version = 'X'
                }
                deps.add project.dependencies.create("org.test:dep2:1.0")
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
        repoModule.parsedPom.scopes.compile?.expectDependency('org.test:dep1:X')
        repoModule.parsedPom.scopes.compile?.expectDependency('org.test:dep2:1.0')
    }

}
