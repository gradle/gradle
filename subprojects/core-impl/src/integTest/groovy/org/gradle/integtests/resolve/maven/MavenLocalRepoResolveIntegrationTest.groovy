/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.Matchers.containsString

class MavenLocalRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        requireOwnGradleUserHomeDir()
        buildFile << """
                repositories {
                    mavenLocal()
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }"""

        using m2Installation
    }

    def "can resolve artifacts from local m2 when user settings.xml does not exist"() {
        given:
        def moduleA = m2Installation.mavenRepo().module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)

    }

    def "can resolve artifacts from local m2 with custom local repository defined in user settings.xml"() {
        given:
        def artifactRepo = mavenLocal("artifactrepo")
        m2Installation.generateUserSettingsFile(artifactRepo)
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "can resolve artifacts from local m2 with custom local repository defined in global settings.xml"() {
        given:
        def artifactRepo = mavenLocal("artifactrepo")
        m2Installation.generateGlobalSettingsFile(artifactRepo)
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "local repository in user settings take precedence over the local repository global settings"() {
        given:
        def globalRepo = mavenLocal("globalArtifactRepo")
        def userRepo = mavenLocal("userArtifactRepo")
        m2Installation.generateGlobalSettingsFile(globalRepo).generateUserSettingsFile(userRepo)
        def moduleA = userRepo.module('group', 'projectA', '1.2').publish()
        globalRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "fail with meaningful error message if settings.xml is invalid"() {
        given:
        m2Installation.userSettingsFile << "invalid content"

        when:
        runAndFail 'retrieve'

        then:
        failure.assertThatCause(containsString(String.format("Non-parseable settings %s:", m2Installation.userSettingsFile.absolutePath)));
    }

    def "mavenLocal is ignored if no local maven repository exists"() {
        given:
        def anotherRepo = maven("another-local-repo")
        def moduleA = anotherRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        and:
        buildFile << """
        repositories{
            maven { url "${anotherRepo.uri}" }
        }
        """

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    @Issue('GRADLE-2034')
    def "mavenLocal fails to resolve artifact if contains pom but not artifact"() {
        given:
        m2Installation.mavenRepo().module('group', 'projectA', '1.2').publishPom()

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause('Could not find group:projectA:1.2')
    }

    @Issue('GRADLE-2034')
    def "mavenLocal skipped if contains pom but no artifact"() {
        given:
        def anotherRepo = maven("another-local-repo")
        m2Installation.mavenRepo().module('group', 'projectA', '1.2').publishPom()
        def moduleARemote = anotherRepo.module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
        repositories{
            maven { url "${anotherRepo.uri}" }
        }
        """

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleARemote)
    }

    @Issue('GRADLE-2034')
    def "mavenLocal resolves pom packaging"() {
        given:
        def childModule = m2Installation.mavenRepo().module('group', 'projectB', '1.2').publish()
        def pomModule = m2Installation.mavenRepo().module('group', 'projectA', '1.2')
        pomModule.packaging = 'pom'
        pomModule.type = 'pom'
        pomModule.dependsOn('group', 'projectB', '1.2')
        pomModule.publishPom()

        when:
        run 'retrieve'

        then:
        hasArtifact(childModule)
    }

    @Issue('GRADLE-2034')
    def "mavenLocal fails to resolve snapshot artifact if contains pom but not artifact"() {
        given:
        m2Installation.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').publishPom()

        and:
        buildFile.text = """
                repositories {
                    mavenLocal()
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2-SNAPSHOT'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }"""

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause('Could not find group:projectA:1.2-SNAPSHOT')
    }

    @Issue('GRADLE-2034')
    def "mavenLocal fails to resolve non-unique snapshot artifact if contains pom but not artifact"() {
        given:
        m2Installation.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').withNonUniqueSnapshots().publishPom()

        and:
        buildFile.text = """
                repositories {
                    mavenLocal()
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2-SNAPSHOT'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }"""

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause('Could not find group:projectA:1.2-SNAPSHOT')
    }

    @Issue('GRADLE-2034')
    def "mavenLocal skipped if contains pom but no artifact for snapshot"() {
        given:
        def anotherRepo = maven("another-local-repo")
        m2Installation.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').publishPom()
        def moduleARemote = anotherRepo.module('group', 'projectA', '1.2-SNAPSHOT').publish()

        and:
        buildFile.text = """
                repositories {
                    mavenLocal()
                    maven { url "${anotherRepo.uri}" }
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2-SNAPSHOT'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }
        """

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleARemote)
    }

    @Issue('GRADLE-2034')
    def "mavenLocal skipped if contains pom but no artifact for non-unique snapshot"() {
        given:
        def anotherRepo = maven("another-local-repo")
        m2Installation.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').withNonUniqueSnapshots().publishPom()
        def moduleARemote = anotherRepo.module('group', 'projectA', '1.2-SNAPSHOT').withNonUniqueSnapshots().publish()

        and:
        buildFile.text = """
                repositories {
                    mavenLocal()
                    maven { url "${anotherRepo.uri}" }
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2-SNAPSHOT'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }
        """

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleARemote)
    }

    def hasArtifact(MavenModule module) {
        def buildDir = file('build')
        def artifactName = module.artifactFile.name
        buildDir.assertHasDescendants(artifactName)
        buildDir.file(artifactName).assertIsCopyOf(module.artifactFile)
    }
}