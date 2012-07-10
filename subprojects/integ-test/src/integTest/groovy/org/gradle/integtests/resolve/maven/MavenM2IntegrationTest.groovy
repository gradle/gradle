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
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.IgnoreIf

import static org.hamcrest.Matchers.containsString

@IgnoreIf({ GradleDistributionExecuter.systemPropertyExecuter == GradleDistributionExecuter.Executer.daemon})
class MavenM2IntegrationTest extends AbstractIntegrationSpec {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    public void "can resolve artifacts from local m2 with not existing user settings.xml"() {
        given:
        def m2 = localM2()

        def moduleA = m2.mavenRepo().module('group', 'projectA', '1.2')
        def moduleB = m2.mavenRepo().module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        and:
        withM2(m2)
        buildFile << """
        repositories {
            mavenLocal()
        }
        configurations { compile }
        dependencies {
            compile 'group:projectA:1.2'
            compile 'group:projectB:9.1'
        }

        task retrieve(type: Sync) {
            from configurations.compile
            into 'build'
        }"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    public void "can resolve artifacts from local m2 with custom localRepository defined in user settings.xml"() {
        given:
        def artifactRepo = new MavenRepository(file("artifactrepo"))
        def m2 = localM2() {
            userSettingsFile << """<settings>
                        <localRepository>${artifactRepo.rootDir.absolutePath}</localRepository>
                    </settings>"""
        }

        def moduleA = artifactRepo.module('group', 'projectA', '1.2')
        def moduleB = artifactRepo.module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        and:

        buildFile << """
        repositories {
            mavenLocal()
        }
        configurations { compile }
        dependencies {
            compile 'group:projectA:1.2'
            compile 'group:projectB:9.1'
        }

        task retrieve(type: Sync) {
            from configurations.compile
            into 'build'
        }"""

        when:
        withM2(m2)

        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    public void "can resolve artifacts from local m2 with custom localRepository defined in global settings.xml"() {
        given:
        def artifactRepo = new MavenRepository(file("artifactrepo"))
        def m2 = localM2() {
            createGlobalSettingsFile(file("global_M2")) << """<settings>
                        <localRepository>${artifactRepo.rootDir.absolutePath}</localRepository>
                    </settings>"""
        }

        def moduleA = artifactRepo.module('group', 'projectA', '1.2')
        def moduleB = artifactRepo.module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        and:
        buildFile << """
        repositories {
            mavenLocal()
        }
        configurations { compile }
        dependencies {
            compile 'group:projectA:1.2'
            compile 'group:projectB:9.1'
        }

        task retrieve(type: Sync) {
            from configurations.compile
            into 'build'
        }"""
        when:
        withM2(m2)
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    public void "localRepository in user settings take precedence over the localRepository global settings"() {
        given:
        def globalRepo = new MavenRepository(file("globalArtifactRepo"))
        def userRepo = new MavenRepository(file("userArtifactRepo"))
        def m2 = localM2() {
            createGlobalSettingsFile(file("global_M2")) << """<settings>
                            <localRepository>${globalRepo.rootDir.absolutePath}</localRepository>
                        </settings>"""
            userSettingsFile << """<settings>
                                    <localRepository>${userRepo.rootDir.absolutePath}</localRepository>
                                </settings>"""

        }

        def userModuleA = userRepo.module('group', 'projectA', '1.2')
        userModuleA.publish()

        def globalModuleA = globalRepo.module('group', 'projectA', '1.2')
        globalModuleA.publishWithChangedContent() // to ensure that resulting artifact
        // has different hash than userModuleA.artifactFile

        and:
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
        when:
        withM2(m2)
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(userModuleA.artifactFile)
    }

    /*
     * TODO RG: if settings.xml is invalid gradle fails with "internal error". Maybe we should change this behaviour as
     *          it's not really an "internal" error.
     */

    public void "fail if settings.xml is invalid"() {
        given:
        def artifactRepo = new MavenRepository(file("artifactrepo"))
        def m2 = localM2() {
            userSettingsFile << "invalid content"
        }

        def moduleA = artifactRepo.module('group', 'projectA', '1.2')
        moduleA.publish()
        and:
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

        when:
        withM2(m2)
        def failure = runAndFail('retrieve')

        then:
        failure.assertThatDescription(containsString("Build aborted because of an internal error"));
    }

    public void "mavenLocal is ignored if not ~/.m2 is defined"() {
        given:
        def userhomePath = file("empy-user-home").absolutePath
        and:
        def anotherRepo = new MavenRepository(file("another-local-repo"))
        def userModuleA = anotherRepo.module('group', 'projectA', '1.2')
        userModuleA.publishWithChangedContent();
        and:

        buildFile << """
        repositories {
            mavenLocal()
            maven { url "${anotherRepo.uri}" }
        }
        configurations { compile }
        dependencies {
            compile 'group:projectA:1.2'
        }

        task retrieve(type: Sync) {
            from configurations.compile
            into 'build'
        }"""

        when:

        executer.withArguments("-Duser.home=${userhomePath}")
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(userModuleA.artifactFile)
    }

    def withM2(M2 m2) {
        def args = ["-Duser.home=${m2.userM2Directory.parentFile.absolutePath}".toString()]
        if (m2.globalMavenDirectory?.exists()) {
            args << "-DM2_HOME=${m2.globalMavenDirectory.absolutePath}".toString()
        }
        executer.withArguments(args)
        executer.withForkingExecuter()
    }

    M2 localM2() {
        TestFile testUserHomeDir = file("user-home")
        TestFile userM2Dir = testUserHomeDir.file(".m2")
        new M2(userM2Dir)
    }

    M2 localM2(Closure configClosure) {
        M2 m2 = localM2()
        configClosure.setDelegate(m2)
        configClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
        configClosure.call()
        m2
    }
}

class M2 {
    final TestFile userM2Directory
    final TestFile userSettingsFile
    TestFile globalMavenDirectory = null;

    public M2(TestFile m2Directory) {
        this.userM2Directory = m2Directory;
        this.userSettingsFile = m2Directory.file("settings.xml")
    }

    MavenRepository mavenRepo() {
        mavenRepo(userM2Directory.file("repository"))
    }

    MavenRepository mavenRepo(TestFile file) {
        new MavenRepository(file)
    }

    TestFile createGlobalSettingsFile(TestFile globalMavenDirectory) {
        this.globalMavenDirectory = globalMavenDirectory;
        globalMavenDirectory.file("conf/settings.xml").createFile()
    }
}