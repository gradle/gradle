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
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    void compilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/broken.java") << "broken";

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':compileJava'.");
        failure.assertHasCause("Compilation failed; see the compiler error output for details.");
    }

    @Test
    void testCompilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/ok.java") << "package org.gradle; class ok { }"
        testFile("src/test/java/org/gradle/broken.java") << "broken"

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':compileTestJava'.");
        failure.assertHasCause("Compilation failed; see the compiler error output for details.");
    }

    @Test
    void handlesTestSrcWhichDoesNotContainAnyTestCases() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/test/java/org/gradle/NotATest.java") << """
package org.gradle;
public class NotATest {}"""

        usingBuildFile(buildFile).withTasks("build").run();
    }

    @Test
    void javadocGenerationFailureBreaksBuild() throws IOException {
        TestFile buildFile = testFile("javadocs.gradle");
        buildFile.write("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/broken.java") << "class Broken { }"

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("javadoc").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':javadoc'.");
        failure.assertHasCause("Javadoc generation failed.");
    }

    @Test
    void handlesResourceOnlyProject() throws IOException {
        TestFile buildFile = testFile("resources.gradle");
        buildFile.write("apply plugin: 'java'");
        testFile("src/main/resources/org/gradle/resource.file") << "test resource"

        usingBuildFile(buildFile).withTasks("build").run();
        testFile("build/resources/main/org/gradle/resource.file").assertExists();
    }

    @Test
    void separatesOutputResourcesFromCompiledClasses() throws IOException {
        //given
        TestFile buildFile = testFile("build.gradle");
        buildFile.write("apply plugin: 'java'");

        testFile("src/main/resources/prod.resource") << ""
        testFile("src/main/java/Main.java") << "class Main {}"
        testFile("src/test/resources/test.resource") << "test resource"
        testFile("src/test/java/TestFoo.java") << "class TestFoo {}"

        //when
        usingBuildFile(buildFile).withTasks("build").run();

        //then
        testFile("build/resources/main/prod.resource").assertExists();
        testFile("build/classes/java/main/prod.resource").assertDoesNotExist();

        testFile("build/resources/test/test.resource").assertExists();
        testFile("build/classes/java/test/test.resource").assertDoesNotExist();

        testFile("build/classes/java/main/Main.class").assertExists();
        testFile("build/classes/java/test/TestFoo.class").assertExists();
    }

    @Test
    void generatesArtifactsWhenVersionIsEmpty() {
        testFile("settings.gradle") << "rootProject.name = 'empty'"
        def buildFile = testFile("build.gradle");
        buildFile << """
apply plugin: 'java'
version = ''
"""

        testFile("src/main/resources/org/gradle/resource.file") << "some resource"

        usingBuildFile(buildFile).withTasks("jar").run();
        testFile("build/libs/empty.jar").assertIsFile();
    }

    @Test
    void "task registered as a builder of resources is executed"() {
        TestFile buildFile = testFile("build.gradle");
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
        def result = usingBuildFile(buildFile).withTasks("classes").run();
        //then
        result.assertTasksExecuted(":compileJava", ":generateResource", ":processResources", ":classes")

        //when
        result = usingBuildFile(buildFile).withTasks("testClasses").run();
        //then
        result.output.contains(":generateTestResource")
    }

    @Test
    void "can recursively build dependent and dependee projects"() {
        testFile("settings.gradle") << "include 'a', 'b', 'c'"
        testFile("build.gradle") << """
allprojects { apply plugin: 'java' }

project(':a') {
    dependencies { compile project(':b') }
}

project(':b') {
    dependencies { compile project(':c') }
}

project(':c') {
}

"""

        def result = inTestDirectory().withTasks('c:buildDependents').run()

        assert result.executedTasks.contains(':a:build')
        assert result.executedTasks.contains(':a:jar')
        assert result.executedTasks.contains(':b:build')
        assert result.executedTasks.contains(':b:jar')
        assert result.executedTasks.contains(':c:build')
        assert result.executedTasks.contains(':c:jar')

        result = inTestDirectory().withTasks('b:buildDependents').run()

        assert result.executedTasks.contains(':a:build')
        assert result.executedTasks.contains(':a:jar')
        assert result.executedTasks.contains(':b:build')
        assert result.executedTasks.contains(':b:jar')
        assert !result.executedTasks.contains(':c:build')
        assert result.executedTasks.contains(':c:jar')

        result = inTestDirectory().withTasks('a:buildDependents').run()

        assert result.executedTasks.contains(':a:build')
        assert result.executedTasks.contains(':a:jar')
        assert !result.executedTasks.contains(':b:build')
        assert result.executedTasks.contains(':b:jar')
        assert !result.executedTasks.contains(':c:build')
        assert result.executedTasks.contains(':c:jar')

        result = inTestDirectory().withTasks('a:buildNeeded').run()

        assert result.executedTasks.contains(':a:build')
        assert result.executedTasks.contains(':a:jar')
        assert result.executedTasks.contains(':b:build')
        assert result.executedTasks.contains(':b:jar')
        assert result.executedTasks.contains(':c:build')
        assert result.executedTasks.contains(':c:jar')

        result = inTestDirectory().withTasks('b:buildNeeded').run()

        assert !result.executedTasks.contains(':a:build')
        assert !result.executedTasks.contains(':a:jar')
        assert result.executedTasks.contains(':b:build')
        assert result.executedTasks.contains(':b:jar')
        assert result.executedTasks.contains(':c:build')
        assert result.executedTasks.contains(':c:jar')

        result = inTestDirectory().withTasks(':c:buildNeeded').run()

        assert !result.executedTasks.contains(':a:build')
        assert !result.executedTasks.contains(':a:jar')
        assert !result.executedTasks.contains(':b:build')
        assert !result.executedTasks.contains(':b:jar')
        assert result.executedTasks.contains(':c:build')
    }

    @Test
    void "project dependency does not drag in source jar from target project"() {
        testFile("settings.gradle") << "include 'a', 'b'"
        testFile("build.gradle") << """
allprojects {
    apply plugin: 'java'

    task sourcesJar(type: Jar) {
        classifier = 'sources'
        from sourceSets.main.allSource
    }

    artifacts {
        archives sourcesJar
    }
}

project(':a') {
    dependencies { compile project(':b') }
    compileJava.doFirst {
        assert classpath.collect { it.name } == ['b.jar']
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
        result.assertTasksExecuted(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar", ":a:compileJava", ":a:processResources", ":a:classes")
    }

    @Test
    void "can add additional jars to published runtime classpath"() {
        testFile("settings.gradle") << "include 'a', 'b'"
        testFile("build.gradle") << """
allprojects {
    apply plugin: 'java'
}

project(':b') {
    sourceSets { extra }

    task additionalJar(type: Jar) {
        classifier = 'extra'
        from sourceSets.extra.output
    }

    artifacts {
        runtime additionalJar
    }
}

project(':a') {
    dependencies { compile project(':b') }
    compileJava.doFirst {
        assert classpath.collect { it.name } == ['b.jar', 'b-extra.jar']
    }
}

"""
        testFile("a/src/main/java/org/gradle/test/PersonImpl.java") << """
package org.gradle.test;
class PersonImpl implements Person { }
"""

        testFile("b/src/extra/java/org/gradle/test/Person.java") << """
package org.gradle.test;
interface Person { }
"""

        inTestDirectory().withTasks("a:classes").run()
    }
}
