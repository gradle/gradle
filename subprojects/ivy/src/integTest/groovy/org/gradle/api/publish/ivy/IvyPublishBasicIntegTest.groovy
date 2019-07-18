/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.publish.ivy

class IvyPublishBasicIntegTest extends AbstractIvyPublishIntegTest {

    def "publishes nothing without defined publication"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        ivyRepo.module('group', 'root', '1.0').assertNotPublished()
    }

    def "publishes empty module when publication has no added component"() {
        given:
        settingsFile << "rootProject.name = 'empty-project'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication)
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('org.gradle.test', 'empty-project', '1.0')
        module.assertPublished()
        module.assertArtifactsPublished("ivy-1.0.xml")

        and:
        with (module.parsedIvy) {
            configurations.isEmpty()
            artifacts.isEmpty()
            dependencies.isEmpty()
            status == "integration"
        }

        and:
        resolveArtifacts(module) {
            withoutModuleMetadata {
                expectFiles()
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can publish simple jar"() {
        given:
        def javaLibrary = javaLibrary(ivyRepo.module('group', 'root', '1.0'))

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'assemble'

        then: "jar is built but not published"
        javaLibrary.assertNotPublished()
        file('build/libs/root-1.0.jar').assertExists()

        when:
        succeeds 'publish'

        then: "jar is published to defined ivy repository"
        javaLibrary.assertPublishedAsJavaModule()
        javaLibrary.removeGradleMetadataRedirection()
        javaLibrary.parsedIvy.status == 'integration'
        javaLibrary.moduleDir.file('root-1.0.jar').assertIsCopyOf(file('build/libs/root-1.0.jar'))

        and:
        resolveArtifacts(javaLibrary) { expectFiles 'root-1.0.jar' }
    }

    def "reports failure publishing when model validation fails"() {
        given:
        settingsFile << "rootProject.name = 'bad-project'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'war'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        from components.web
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasCause("Ivy publication 'ivy' cannot include multiple components")
    }

    def "publishes to all defined repositories"() {
        given:
        def ivyRepo2 = ivy("ivy-repo-2")

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                    ivy { url "${ivyRepo2.uri}" }
                }
                publications {
                    ivy(IvyPublication)
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('org.gradle.test', 'root', '1.0')
        module.assertPublished()
        def module2 = ivyRepo2.module('org.gradle.test', 'root', '1.0')
        module2.assertPublished()
    }

}
