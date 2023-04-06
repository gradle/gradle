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
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

class MavenLocalRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        using m2
        buildFile << """
                repositories {
                    mavenLocal {
                        content {
                            excludeGroup 'unused'
                        }
                    }
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:1.2'
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }"""
    }

    def "can resolve artifacts from local m2 when user settings.xml does not exist"() {
        given:
        def moduleA = m2.mavenRepo().module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "can resolve artifacts from local m2 with custom local repository defined in user settings.xml"() {
        given:
        def artifactRepo = mavenLocal("artifactrepo")
        m2.generateUserSettingsFile(artifactRepo)
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "can resolve artifacts from local m2 with custom local repository defined in global settings.xml"() {
        given:
        def sysPropRepo = mavenLocal("artifactrepo")
        m2.generateGlobalSettingsFile(sysPropRepo)
        def moduleA = sysPropRepo.module('group', 'projectA', '1.2').publish()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "can resolve artifacts from local m2 with custom local repository defined by system-property"() {
        given:
        def artifactRepo = mavenLocal("artifactrepo")
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        when:
        args "-Dmaven.repo.local=${artifactRepo.rootDir.getAbsolutePath()}"
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "local repository in user settings take precedence over the local repository global settings"() {
        given:
        def globalRepo = mavenLocal("globalArtifactRepo")
        def userRepo = mavenLocal("userArtifactRepo")
        m2.generateGlobalSettingsFile(globalRepo).generateUserSettingsFile(userRepo)
        def moduleA = userRepo.module('group', 'projectA', '1.2').publish()
        globalRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "local repository in System Property take precedence over the local repository user settings"() {
        given:
        def userRepo = mavenLocal("userArtifactRepo")
        m2.generateUserSettingsFile(userRepo)
        def sysPropRepo = mavenLocal("artifactrepo")
        def moduleA = sysPropRepo.module('group', 'projectA', '1.2').publish()
        userRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        when:
        args "-Dmaven.repo.local=${sysPropRepo.rootDir.getAbsolutePath()}"
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def "fail with meaningful error message if settings.xml is invalid"() {
        given:
        m2.userSettingsFile << "invalid content"

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause("Unable to parse local Maven settings: " + m2.userSettingsFile.absolutePath)
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
        m2.mavenRepo().module('group', 'projectA', '1.2').publishPom()

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause('Could not find group:projectA:1.2')
    }

    def "mavenLocal reports and recovers from missing module"() {
        def module = m2.mavenRepo().module('group', 'projectA', '1.2')

        when:
        runAndFail 'retrieve'

        then:
        failure.assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.pomFile.toURL()}
Required by:
""")
        failure.assertHasResolutions(repositoryHint("Maven POM"),
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

        when:
        module.publish()

        then:
        succeeds 'retrieve'
    }

    @Issue('GRADLE-2034')
    def "mavenLocal skipped if contains pom but no artifact and there is another repository available"() {
        given:
        def anotherRepo = maven("another-local-repo")
        m2.mavenRepo().module('group', 'projectA', '1.2').publishPom()
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

    def "mavenLocal includes jar for module with packaging 'pom'"() {
        given:
        m2.mavenRepo().module('group', 'projectB', '1.2').publish()
        def pomModule = m2.mavenRepo().module('group', 'projectA', '1.2')
        pomModule.packaging = 'pom'
        pomModule.dependsOn('group', 'projectB', '1.2')
        pomModule.artifact(type: "jar")
        pomModule.publish()

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants("projectA-1.2.jar", "projectB-1.2.jar")
    }

    def "mavenLocal ignores missing jar for module with packaging 'pom'"() {
        given:
        m2.mavenRepo().module('group', 'projectB', '1.2').publish()
        def pomModule = m2.mavenRepo().module('group', 'projectA', '1.2')
        pomModule.packaging = 'pom'
        pomModule.dependsOn('group', 'projectB', '1.2')
        pomModule.publishPom()

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants("projectB-1.2.jar")
    }

    @Issue('GRADLE-2034')
    def "mavenLocal fails to resolve snapshot artifact if contains pom but not artifact"() {
        given:
        m2.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').publishPom()

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
        m2.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').withNonUniqueSnapshots().publishPom()

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
        m2.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').publishPom()
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
        m2.mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').withNonUniqueSnapshots().publishPom()
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

    @Issue("gradle/gradle#11321")
    def "mavenLocal version listing works without weaking metadata source configuration"() {
        given:
        m2.mavenRepo().module('group', 'projectA', '1.1').publish()
        def module = m2.mavenRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile.text = """
                repositories {
                    mavenLocal()
                }
                configurations { compile }
                dependencies {
                    compile 'group:projectA:[1.0,2.0['
                }

                task retrieve(type: Sync) {
                    from configurations.compile
                    into 'build'
                }
        """

        when:
        run 'retrieve'

        then:
        hasArtifact(module)

    }

    def hasArtifact(MavenModule module) {
        def buildDir = file('build')
        def artifactName = module.artifactFile.name
        buildDir.assertHasDescendants(artifactName)
        buildDir.file(artifactName).assertIsCopyOf(module.artifactFile)
    }
}
