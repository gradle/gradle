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

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue

/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishBasicIntegTest extends AbstractMavenPublishIntegTest {

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    MavenLocalRepository localM2Repo

    def "setup"() {
        localM2Repo = m2.mavenRepo()
    }

    def "publishes nothing without defined publication"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        mavenRepo.module('group', 'root', '1.0').assertNotPublished()
    }

    def "publishes empty pom when publication has no added component"() {
        given:
        settingsFile << "rootProject.name = 'empty-project'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication)
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'empty-project', '1.0')
        module.assertPublishedAsPomModule()
        module.parsedPom.scopes.isEmpty()

        and:
        resolveArtifacts(module) {
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles()
            }
        }
    }

    def "can publish simple component"() {
        given:
        using m2
        def repoModule = javaLibrary(mavenRepo.module('group', 'root', '1.0'))
        def localModule = javaLibrary(localM2Repo.module('group', 'root', '1.0'))

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'assemble'

        then: "jar is built but not published"
        repoModule.assertNotPublished()
        localModule.assertNotPublished()
        file('build/libs/root-1.0.jar').assertExists()

        when:
        succeeds 'publish'

        then: "jar is published to defined maven repository"
        repoModule.assertPublished()
        localModule.assertNotPublished()

        and:
        repoModule.rootMetaData.groupId == "group"
        repoModule.rootMetaData.artifactId == "root"
        repoModule.rootMetaData.versions == ["1.0"]
        repoModule.rootMetaData.releaseVersion == "1.0"
        repoModule.rootMetaData.latestVersion == "1.0"

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        localModule.assertPublished()

        and:
        localModule.rootMetaData.groupId == "group"
        localModule.rootMetaData.artifactId == "root"
        localModule.rootMetaData.versions == ["1.0"]
        localModule.rootMetaData.releaseVersion == "1.0"
        localModule.rootMetaData.latestVersion == "1.0"

        and:
        resolveArtifacts(repoModule) {
            expectFiles 'root-1.0.jar'
        }
    }

    def "can republish simple component"() {
        given:
        using m2

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
        succeeds 'publish', 'publishToMavenLocal'
        buildFile.text = buildFile.text.replace("1.0", "2.0")

        def repoModule = javaLibrary(mavenRepo.module('group', 'root', '2.0'))
        def localModule = javaLibrary(localM2Repo.module('group', 'root', '2.0'))

        when:
        succeeds 'publish', 'publishToMavenLocal'

        then:
        repoModule.assertPublished()
        localModule.assertPublished()

        and:
        repoModule.rootMetaData.groupId == "group"
        repoModule.rootMetaData.artifactId == "root"
        repoModule.rootMetaData.versions == ["1.0", "2.0"]
        repoModule.rootMetaData.releaseVersion == "2.0"
        repoModule.rootMetaData.latestVersion == "2.0"

        and:
        localModule.rootMetaData.groupId == "group"
        localModule.rootMetaData.artifactId == "root"
        localModule.rootMetaData.versions == ["1.0", "2.0"]
        localModule.rootMetaData.releaseVersion == "2.0"
        localModule.rootMetaData.latestVersion == "2.0"
    }

    def "can publish to custom maven local repo defined in settings.xml"() {
        given:
        def customLocalRepo = new MavenLocalRepository(file("custom-maven-local"))
        m2.generateUserSettingsFile(customLocalRepo)
        using m2

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publishToMavenLocal'

        then:
        localM2Repo.module("group", "root", "1.0").assertNotPublished()
        javaLibrary(customLocalRepo.module("group", "root", "1.0")).assertPublished()
    }

    def "reports failure publishing when model validation fails"() {
        given:
        settingsFile << "rootProject.name = 'bad-project'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'war'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        from components.web
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasCause("Maven publication 'maven' cannot include multiple components")
    }

    def "publishes to all defined repositories"() {
        given:
        def mavenRepo2 = maven("maven-repo-2")

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                    maven { url = "${mavenRepo2.uri}" }
                }
                publications {
                    maven(MavenPublication)
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'root', '1.0')
        module.assertPublished()
        def module2 = mavenRepo2.module('org.gradle.test', 'root', '1.0')
        module2.assertPublished()
    }

    def "can publish custom PublishArtifact"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
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
            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        artifact customArtifact
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle.test', 'root', '1.0')
        module.assertPublished()
    }

    def "warns when trying to publish a transitive = false variant"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            configurations {
                apiElements {
                    transitive = false
                }
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        expect:
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
                id 'maven-publish'
            }

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }

            dependencies {
                implementation enforcedPlatform('org:platform:1.0')
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        fails ':publish'

        then:
        failure.assertHasCause """Invalid publication 'maven':
  - Variant 'runtimeElements' contains a dependency on enforced platform 'org:platform'
In general publishing dependencies to enforced platforms is a mistake: enforced platforms shouldn't be used for published components because they behave like forced dependencies and leak to consumers. This can result in hard to diagnose dependency resolution errors. If you did this intentionally you can disable this check by adding 'enforced-platform' to the suppressed validations of the :generateMetadataFileForMavenPublication task."""
    }

    @Issue("https://github.com/gradle/gradle/issues/15009")
    def "can disable validation of publication of dependencies on enforced platforms"() {
        mavenRepo.module("org", "platform", "1.0").asGradlePlatform().publish()
        settingsFile << """
            rootProject.name = 'publish'
        """
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            group = 'com.acme'
            version = '0.999'

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }

            dependencies {
                implementation enforcedPlatform('org:platform:1.0')
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            tasks.named('generateMetadataFileForMavenPublication') {
                suppressedValidationErrors.add('enforced-platform')
            }
        """

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generateMetadataFileForMavenPublication', ':publishMavenPublicationToMavenRepository'
    }
}
