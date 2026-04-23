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

package org.gradle.integtests

import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test
import org.junit.experimental.categories.Category

@SuppressWarnings('IntegrationTestFixtures')
class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    void compilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle")
        buildFile.writelns("apply plugin: 'java'")
        testFile("src/main/java/org/gradle/broken.java") << "broken"

        ExecutionFailure failure = executer.withTasks("build").runWithFailure()

        failure.assertHasDescription("Execution failed for task ':compileJava' (registered by plugin class 'org.gradle.api.plugins.JavaBasePlugin').")
        failure.assertHasCause(CompilationFailedException.COMPILATION_FAILED_DETAILS_BELOW)
        failure.assertHasResolution(CompilationFailedException.RESOLUTION_MESSAGE)
    }

    @Test
    void testCompilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle")
        buildFile.writelns("apply plugin: 'java'")
        testFile("src/main/java/org/gradle/ok.java") << "package org.gradle; class ok { }"
        testFile("src/test/java/org/gradle/broken.java") << "broken"

        ExecutionFailure failure = executer.withTasks("build").runWithFailure()

        failure.assertHasDescription("Execution failed for task ':compileTestJava' (registered by plugin class 'org.gradle.api.plugins.JavaBasePlugin').")
        failure.assertHasCause(CompilationFailedException.COMPILATION_FAILED_DETAILS_BELOW)
        failure.assertHasResolution(CompilationFailedException.RESOLUTION_MESSAGE)
    }

    @Test
    void javadocGenerationFailureBreaksBuild() throws IOException {
        TestFile buildFile = testFile("build.gradle")
        buildFile.write("apply plugin: 'java'")
        testFile("src/main/java/org/gradle/broken.java") << "class Broken { }"

        ExecutionFailure failure = executer.withTasks("javadoc").runWithFailure()

        failure.assertHasDescription("Execution failed for task ':javadoc' (registered by plugin 'org.gradle.java').")
        failure.assertHasCause("Javadoc generation failed.")
    }

    @Test
    void handlesResourceOnlyProject() throws IOException {
        TestFile buildFile = testFile("build.gradle")
        buildFile.write("apply plugin: 'java'")
        testFile("src/main/resources/org/gradle/resource.file") << "test resource"

        executer.withTasks("build").run()
        testFile("build/resources/main/org/gradle/resource.file").assertExists()
    }

    @Test
    void separatesOutputResourcesFromCompiledClasses() throws IOException {
        given:
        testFile("build.gradle") << """
            plugins {
                id("java")
            }

           ${mavenCentralRepository()}

            testing.suites.test.useJUnit()
        """
        testFile("src/main/resources/prod.resource") << ""
        testFile("src/main/java/Main.java") << "class Main {}"
        testFile("src/test/resources/test.resource") << "test resource"
        testFile("src/test/java/TestFoo.java") << """
        public class TestFoo {
            @org.junit.Test
            public void test() {}
        }
        """

        when:
        executer.withTasks("build").run()

        then:
        testFile("build/resources/main/prod.resource").assertExists()
        testFile("build/classes/java/main/prod.resource").assertDoesNotExist()

        testFile("build/resources/test/test.resource").assertExists()
        testFile("build/classes/java/test/test.resource").assertDoesNotExist()

        testFile("build/classes/java/main/Main.class").assertExists()
        testFile("build/classes/java/test/TestFoo.class").assertExists()
    }

    @Test
    void generatesArtifactsWhenVersionIsEmpty() {
        testFile("settings.gradle") << "rootProject.name = 'empty'"
        def buildFile = testFile("build.gradle")
        buildFile << """
            apply plugin: 'java'
            version = ''
        """

        testFile("src/main/resources/org/gradle/resource.file") << "some resource"

        executer.withTasks("jar").run()
        testFile("build/libs/empty.jar").assertIsFile()
    }

    @Test
    void "task registered as a builder of resources is executed"() {
        TestFile buildFile = testFile("build.gradle")
        buildFile << '''
            apply plugin: 'java'

            task generateResource
            task generateTestResource
            task notRegistered

            sourceSets.main.output.dir "$buildDir/generatedResources", builtBy: 'generateResource'
            sourceSets.main.output.dir "$buildDir/generatedResourcesWithoutBuilder"

            sourceSets.test.output.dir "$buildDir/generatedTestResources", builtBy: 'generateTestResource'
        '''

        //when
        def result = executer.withTasks("classes").run()
        //then
        result.assertTasksScheduled(":compileJava", ":generateResource", ":processResources", ":classes")

        //when
        result = executer.withTasks("testClasses").run()
        //then
        result.output.contains(":generateTestResource")
    }

    @Test
    @ToBeFixedForIsolatedProjects(because = "allprojects, configure projects from root")
    @Category(Flaky.class) // https://github.com/gradle/gradle-private/issues/4442
    void "can recursively build dependent and dependee projects"() {
        createDirs("a", "b", "c")
        testFile("settings.gradle") << "include 'a', 'b', 'c'"
        testFile("build.gradle") << """
            allprojects { apply plugin: 'java-library' }

            project(':a') {
                dependencies { api project(':b') }
            }

            project(':b') {
                dependencies { api project(':c') }
            }

            project(':c') {
            }
        """

        def result = inTestDirectory().withTasks('c:buildDependents').run()

        assert result.assertTaskScheduled(':a:build')
        assert result.assertTaskScheduled(':a:jar')
        assert result.assertTaskScheduled(':b:build')
        assert result.assertTaskScheduled(':b:jar')
        assert result.assertTaskScheduled(':c:build')
        assert result.assertTaskScheduled(':c:jar')

        result = inTestDirectory().withTasks('b:buildDependents').run()

        assert result.assertTaskScheduled(':a:build')
        assert result.assertTaskScheduled(':a:jar')
        assert result.assertTaskScheduled(':b:build')
        assert result.assertTaskScheduled(':b:jar')
        assert result.assertTasksNotScheduled(':c:build')
        assert result.assertTaskScheduled(':c:jar')

        result = inTestDirectory().withTasks('a:buildDependents').run()

        assert result.assertTaskScheduled(':a:build')
        assert result.assertTaskScheduled(':a:jar')
        assert result.assertTasksNotScheduled(':b:build')
        assert result.assertTaskScheduled(':b:jar')
        assert result.assertTasksNotScheduled(':c:build')
        assert result.assertTaskScheduled(':c:jar')

        result = inTestDirectory().withTasks('a:buildNeeded').run()

        assert result.assertTaskScheduled(':a:build')
        assert result.assertTaskScheduled(':a:jar')
        assert result.assertTaskScheduled(':b:build')
        assert result.assertTaskScheduled(':b:jar')
        assert result.assertTaskScheduled(':c:build')
        assert result.assertTaskScheduled(':c:jar')

        result = inTestDirectory().withTasks('b:buildNeeded').run()

        assert result.assertTasksNotScheduled(':a:build')
        assert result.assertTasksNotScheduled(':a:jar')
        assert result.assertTaskScheduled(':b:build')
        assert result.assertTaskScheduled(':b:jar')
        assert result.assertTaskScheduled(':c:build')
        assert result.assertTaskScheduled(':c:jar')

        result = inTestDirectory().withTasks(':c:buildNeeded').run()

        assert result.assertTasksNotScheduled(':a:build')
        assert result.assertTasksNotScheduled(':a:jar')
        assert result.assertTasksNotScheduled(':b:build')
        assert result.assertTasksNotScheduled(':b:jar')
        assert result.assertTaskScheduled(':c:build')
    }

    @Test
    @ToBeFixedForIsolatedProjects(because = "allprojects, configure projects from root")
    @Category(Flaky.class) // https://github.com/gradle/gradle-private/issues/4442
    void "project dependency does not drag in source jar from target project"() {
        createDirs("a", "b")
        testFile("settings.gradle") << "include 'a', 'b'"
        testFile("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'

                java {
                    withSourcesJar()
                }
            }

            project(':a') {
                dependencies { implementation project(':b') }
                compileJava.doFirst {
                    assert classpath.collect { it.name } == ['main']
                }
            }
        """
        testFile("a/src/main/java/org/gradle/test/PersonImpl.java") << """
            package org.gradle.test;
            class PersonImpl implements Person { }
        """

        testFile("b/src/main/java/org/gradle/test/Person.java") << """
            package org.gradle.test;
            interface Person { }
        """

        def result = inTestDirectory().withTasks("a:classes").run()
        result.assertTasksScheduled(":b:compileJava", ":a:compileJava", ":a:processResources", ":a:classes")
    }

}
