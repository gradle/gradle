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

import spock.lang.Issue

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

    def "can publish custom PublishArtifact"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'
            group = 'org.gradle.test'
            version = '1.0'
            def writeFileProvider = tasks.register("writeFile") {
                doLast {
                    try (FileOutputStream out = new FileOutputStream("customArtifact.jar")) {}
                }
            }
            def customArtifact = new PublishArtifact() {
                @Override
                String getName() {
                    return "customArtifact"
                }
                @Override
                String getExtension() {
                    return "jar"
                }
                @Override
                String getType() {
                    return "jar"
                }
                @Override
                String getClassifier() {
                    return null
                }
                @Override
                File getFile() {
                    return new File("customArtifact.jar")
                }
                @Override
                Date getDate() {
                    return new Date()
                }
                @Override
                TaskDependency getBuildDependencies() {
                    return new TaskDependency() {
                        @Override
                        Set<? extends Task> getDependencies(Task task) {
                            return Collections.singleton(writeFileProvider.get())
                        }
                    }
                }
            }
            ${ivyTestRepository()}
            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact customArtifact
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('org.gradle.test', 'root', '1.0')
        module.assertPublished()
    }

    def "warns when trying to publish a transitive = false variant"() {
        given:
        def javaLibrary = javaLibrary(ivyRepo.module('group', 'root', '1.0'))

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            configurations {
                apiElements {
                    transitive = false
                }
                runtimeElements {
                    transitive = false
                }
            }

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

        expect: "build warned about transitive = true variant"
        executer.withStackTraceChecksDisabled()
        executer.expectDeprecationWarning("Publication ignores 'transitive = false' at configuration level. This behavior is deprecated. Consider using 'transitive = false' at the dependency level if you need this to be published.")
        succeeds 'publish'
    }

    @Issue("https://github.com/gradle/gradle/issues/15009")
    def "fails publishing if a variant contains a dependency on an enforced platform"() {
        settingsFile << """
            rootProject.name = 'publish'
        """
        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
            }

            dependencies {
                implementation enforcedPlatform('org:platform:1.0')
            }

            ${emptyJavaClasspath()}

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
        fails ':publish'

        then:
        failure.assertHasCause """Invalid publication 'ivy':
  - Variant 'runtimeElements' contains a dependency on enforced platform 'org:platform'
In general publishing dependencies to enforced platforms is a mistake: enforced platforms shouldn't be used for published components because they behave like forced dependencies and leak to consumers. This can result in hard to diagnose dependency resolution errors. If you did this intentionally you can disable this check by adding 'enforced-platform' to the suppressed validations of the :generateMetadataFileForIvyPublication task."""
    }

    @Issue("https://github.com/gradle/gradle/issues/15009")
    def "can disable validation of publication of dependencies on enforced platforms"() {
        settingsFile << """
            rootProject.name = 'publish'
        """
        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
            }

            group = 'com.acme'
            version = '0.999'

            ${emptyJavaClasspath()}

            dependencies {
                implementation enforcedPlatform('org:platform:1.0')
            }

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

            tasks.named('generateMetadataFileForIvyPublication') {
                suppressedValidationErrors.add('enforced-platform')
            }
        """

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generateMetadataFileForIvyPublication', ':publishIvyPublicationToIvyRepository'
    }
}
