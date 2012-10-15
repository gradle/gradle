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

import org.gradle.integtests.fixture.M2Installation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.MavenModule
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestFile
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

    public void "can resolve artifacts from local m2 with not existing user settings.xml"() {
        given:
        def m2 = localM2()
        def moduleA = m2.mavenRepo().module('group', 'projectA', '1.2')
        moduleA.publish()
        and:
        withM2(m2)

        when:
        run 'retrieve'

        then:
        hasArtifact(moduleA)

    }

    public void "can resolve artifacts from local m2 with custom localRepository defined in user settings.xml"() {
        given:
        def artifactRepo = maven("artifactrepo")
        def m2 = localM2() {
            userSettingsFile << """<settings>
                        <localRepository>${artifactRepo.rootDir.absolutePath}</localRepository>
                    </settings>"""
        }
        def moduleA = artifactRepo.module('group', 'projectA', '1.2')
        moduleA.publish()

        when:
        withM2(m2)
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "can resolve artifacts from local m2 with custom localRepository defined in global settings.xml"() {
        given:
        def artifactRepo = maven("artifactrepo")
        def m2 = localM2() {
            createGlobalSettingsFile(file("global_M2")) << """<settings>
                        <localRepository>${artifactRepo.rootDir.absolutePath}</localRepository>
                    </settings>"""
        }

        def moduleA = artifactRepo.module('group', 'projectA', '1.2')
        moduleA.publish()

        when:
        withM2(m2)
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "localRepository in user settings take precedence over the localRepository global settings"() {
        given:
        def globalRepo = maven("globalArtifactRepo")
        def userRepo = maven("userArtifactRepo")
        def m2 = localM2() {
            createGlobalSettingsFile(file("global_M2")) << """<settings>
                            <localRepository>${globalRepo.rootDir.absolutePath}</localRepository>
                        </settings>"""
            userSettingsFile << """<settings>
                                    <localRepository>${userRepo.rootDir.absolutePath}</localRepository>
                                </settings>"""

        }

        def moduleA = userRepo.module('group', 'projectA', '1.2')
        moduleA.publish()

        def globalModuleA = globalRepo.module('group', 'projectA', '1.2')
        globalModuleA.publishWithChangedContent() // to ensure that resulting artifact
                                                  // has different hash than userModuleA.artifactFile

        when:
        withM2(m2)
        run 'retrieve'

        then:
        hasArtifact(moduleA)
    }

    public void "fail with meaningful error message if settings.xml is invalid"() {
        given:
        def m2 = localM2() {
            userSettingsFile << "invalid content"
        }

        when:
        withM2(m2)
        def failure = runAndFail('retrieve')

        then:
        failure.assertThatCause(containsString(String.format("Non-parseable settings %s:", m2.userSettingsFile.absolutePath)));
    }

    public void "mavenLocal is ignored if no local maven repository exists"() {
        given:
        def anotherRepo = maven("another-local-repo")
        def moduleA = anotherRepo.module('group', 'projectA', '1.2')
        moduleA.publishWithChangedContent();

        when:
        buildFile << """
        repositories{
            maven { url "${anotherRepo.uri}" }
        }
        """

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
        def args = ["-Duser.home=${m2.userM2Directory.parentFile.absolutePath}".toString()]
        if (m2.globalMavenDirectory?.exists()) {
            executer.withEnvironmentVars(M2_HOME:m2.globalMavenDirectory.absolutePath)
        }
        executer.withArguments(args)
    }

    M2Installation localM2() {
        TestFile testUserHomeDir = distribution.getUserHomeDir()
        TestFile userM2Dir = testUserHomeDir.file(".m2")
        new M2Installation(userM2Dir)
    }

    M2Installation localM2(Closure configClosure) {
        M2Installation m2 = localM2()
        configClosure.setDelegate(m2)
        configClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
        configClosure.call()
        m2
    }
}