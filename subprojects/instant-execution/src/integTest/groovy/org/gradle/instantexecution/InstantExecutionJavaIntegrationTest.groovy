/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.test.fixtures.archive.ZipTestFixture

class InstantExecutionJavaIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    protected InstantExecutionBuildOperationsFixture instantExecution

    def setup() {
        instantExecution = newInstantExecutionFixture()
        executer.noDeprecationChecks()
    }

    def "assemble on Java build with no dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """

        expect:
        instantRun "assemble"
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        jarFile.isFile()

        when:
        classFile.delete()
        jarFile.delete()
        instantRun "assemble"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        classFile.isFile()
        new ZipTestFixture(jarFile).assertContainsFile("Thing.class")
    }

    def "assemble on Java build with multiple source directories"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
            
            sourceSets.main.java.srcDir("src/common/java") 
        """
        file("src/common/java/OtherThing.java") << """
            class OtherThing {
            }
        """
        file("src/main/java/Thing.java") << """
            class Thing extends OtherThing {
            }
        """

        expect:
        instantRun "assemble"
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        jarFile.isFile()

        when:
        classFile.delete()
        jarFile.delete()
        instantRun "assemble"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        classFile.isFile()
        new ZipTestFixture(jarFile).assertContainsFile("Thing.class")
        new ZipTestFixture(jarFile).assertContainsFile("OtherThing.class")
    }

    def "assemble on specific project of multi-project Java build"() {
        given:
        settingsFile << """
            include("a", "b")
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            project(":b") {
                dependencies {
                    implementation(project(":a"))
                }
            }
        """
        file("a/src/main/java/a/A.java") << """
            package a;
            public class A {}
        """
        file("b/src/main/java/b/B.java") << """
            package b;
            public class B extends a.A {}
        """

        when:
        instantRun ":b:assemble"

        and:
        file("b/build/classes/java/main/b/B.class").delete()
        def jarFile = file("b/build/libs/b.jar")
        jarFile.delete()

        and:
        instantRun ":b:assemble"

        then:
        new ZipTestFixture(jarFile).assertContainsFile("b/B.class")
    }

    def "assemble on Java application build with no dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'application' }
            application.mainClassName = 'Thing'
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """

        expect:
        instantRun "assemble"
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":startScripts", ":distTar", ":distZip", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        jarFile.delete()

        when:
        classFile.delete()
        instantRun "assemble"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":startScripts", ":distTar", ":distZip", ":assemble")
        classFile.isFile()
        jarFile.isFile()
    }
}
