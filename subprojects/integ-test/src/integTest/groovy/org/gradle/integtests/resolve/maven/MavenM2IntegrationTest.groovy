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
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.util.TestFile

class MavenM2IntegrationTest extends AbstractIntegrationSpec {
    public void "can resolve artifacts from local m2 with undefinded settings.xml"() {
        given:
        def m2 = withLocalM2()

        def moduleA = m2.mavenRepo().module('group', 'projectA', '1.2')
        def moduleB = m2.mavenRepo().module('group', 'projectB', '9.1')
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
        }
        """

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleB.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    M2 withLocalM2() {
        TestFile testUserHomeDir = file("testuserhome")
        executer.withArguments("-Duser.home=${testUserHomeDir.absolutePath}")
        TestFile userM2Dir = testUserHomeDir.file(".m2")
        new M2(userM2Dir)
    }

    M2 withLocalM2(Closure configClosure) {
        M2 m2 = withLocalM2()
        configClosure.setDelegate(m2)
        configClosure.setResolveStrategy(Closure.DELEGATE_ONLY)
        configClosure.call()
        m2
    }
}

class M2 {
    final TestFile m2Directory

    public M2(TestFile m2Directory) {
        this.m2Directory = m2Directory;

    }

    MavenRepository mavenRepo() {
        mavenRepo(m2Directory.file("repository"))
    }

    MavenRepository mavenRepo(TestFile file) {
        new MavenRepository(file)
    }
}