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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import static org.hamcrest.Matchers.containsString

class MavenLocalRepoResolveIntegrationTest extends AbstractIntegrationSpec {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    public void setup() {
        requireOwnUserHomeDir()
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
    }

    public void "can resolve artifacts from local m2 when user settings.xml does not exist"() {
        given:
        def m2 = localM2()
        def moduleA = m2.mavenRepo().module('group', 'projectA', '1.2').publish()

        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)

    }

    public void "can resolve artifacts from local m2 with custom local repository defined in user settings.xml"() {
        given:
        def artifactRepo = maven("artifactrepo")
        def m2 = localM2().generateUserSettingsFile(artifactRepo)
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "can resolve artifacts from local m2 with custom local repository defined in global settings.xml"() {
        given:
        def artifactRepo = maven("artifactrepo")
        def m2 = localM2().generateGlobalSettingsFile(artifactRepo)
        def moduleA = artifactRepo.module('group', 'projectA', '1.2').publish()

        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "local repository in user settings take precedence over the local repository global settings"() {
        given:
        def globalRepo = maven("globalArtifactRepo")
        def userRepo = maven("userArtifactRepo")
        def m2 = localM2().generateGlobalSettingsFile(globalRepo).generateUserSettingsFile(userRepo)
        def moduleA = userRepo.module('group', 'projectA', '1.2').publish()
        globalRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "fail with meaningful error message if settings.xml is invalid"() {
        given:
        def m2 = localM2()
        m2.userSettingsFile << "invalid content"

        and:
        withM2(m2)

        when:
        def failure = runAndFail('retrieve')

        then:
        failure.assertThatCause(containsString(String.format("Non-parseable settings %s:", m2.userSettingsFile.absolutePath)));
    }

    public void "mavenLocal is ignored if no local maven repository exists"() {
        given:
        def m2 = localM2()
        def anotherRepo = maven("another-local-repo")
        def moduleA = anotherRepo.module('group', 'projectA', '1.2').publishWithChangedContent()

        and:
        buildFile << """
        repositories{
            maven { url "${anotherRepo.uri}" }
        }
        """

        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    def hasArtifact(MavenModule module) {
        def buildDir = file('build')
        def artifactName = module.artifactFile.name
        buildDir.assertHasDescendants(artifactName)
        buildDir.file(artifactName).assertIsCopyOf(module.artifactFile)
    }

    def withM2(M2Installation m2) {
        executer.withUserHomeDir(m2.userHomeDir)
        if (m2.globalMavenDirectory?.exists()) {
            executer.withEnvironmentVars(M2_HOME:m2.globalMavenDirectory.absolutePath)
        }
    }

    M2Installation localM2() {
        new M2Installation(testDir)
    }
}