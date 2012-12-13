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



package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule
/**
 * Tests maven POM customisation
 */
class MavenPublishPomCustomisationIntegTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def "can customise pom xml"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven {
                        pom.withXml {
                            asNode().version[0].value = "foo"
                            asNode().appendNode('description', "custom-description")

                            def dependency = asNode().appendNode('dependencies').appendNode('dependency')
                            dependency.appendNode('groupId', 'junit')
                            dependency.appendNode('artifactId', 'junit')
                            dependency.appendNode('version', '4.1')
                            dependency.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('group', 'root', 'foo')
        module.assertPublishedAsJavaModule()
        module.pom.description == 'custom-description'
        module.pom.scopes.runtime.assertDependsOn("junit", "junit", "4.1")
    }

    def "has reasonable error message when withXml fails"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven {
                        pom.withXml {
                            asNode().foo = 'this is not a real element'
                        }
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Could not apply withXml() to generated POM")
        failure.assertHasCause("No such property: foo for class: groovy.util.Node")
    }
}
