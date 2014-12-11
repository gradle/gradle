/*
 * Copyright 2014 the original author or authors.
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

class MavenPublicationVersionRangeIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    public void "version range is mapped to maven syntax in published pom file"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.9'

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

            dependencies {
                compile "group:projectA:latest.release"
                compile "group:projectB:latest.integration"
                runtime "group:projectC:+"
            }"""

        when:
        run "publish"

        then:
        mavenModule.assertPublishedAsJavaModule()
        mavenModule.parsedPom.scopes.keySet() == ["runtime"] as Set
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("group:projectA:RELEASE", "group:projectB:LATEST", "group:projectC:LATEST")
    }

    public void "publishing pom with dependency versions 'x.+' 'x+' generates warning"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.9'

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

            dependencies {
                compile "group:projectA:1.+"
                runtime "group:projectB:1+"
            }"""

        when:
        run "publish"

        then:
        mavenModule.assertPublishedAsJavaModule()
        mavenModule.parsedPom.scopes.keySet() == ["runtime"] as Set
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("group:projectA:1.+", "group:projectB:1+")

        and:
        output.contains("Generating POM with maven incompatible version string '1+'.")
        output.contains("Generating POM with maven incompatible version string '1.+'.")
    }
}