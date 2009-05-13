/*
 * Copyright 2007 the original author or authors.
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

import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class GroovyProjectSampleIntegrationTest {
    static final String TEST_PROJECT_NAME = 'testproject'

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void groovyProjectSamples() {
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'

        List mainFiles = ['JavaPerson', 'GroovyPerson', 'GroovyJavaPerson']
        List excludedFiles = ['ExcludeJava', 'ExcludeGroovy', 'ExcludeGroovyJava']
        List testFiles = ['JavaPersonTest', 'GroovyPersonTest', 'GroovyJavaPersonTest']

        File groovyProjectDir = new File(dist.samplesDir, 'groovy/multiproject')
        File testProjectDir = new File(groovyProjectDir, TEST_PROJECT_NAME)

        // Build libs
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'libs').run()
        mainFiles.each { new TestFile(testProjectDir, packagePrefix, it + ".class").assertExists() }
        excludedFiles.each { new TestFile(testProjectDir, false, packagePrefix, it + ".class").assertDoesNotExist() }

        testFiles.each { new TestFile(testProjectDir, testPackagePrefix, it + ".class").assertExists() }

        // The test produce marker files with the name of the test class
        testFiles.each { new TestFile(testProjectDir, 'build', it).assertExists() }

        String unjarPath = "$testProjectDir/build/unjar"
        AntBuilder ant = new AntBuilder()
        ant.unjar(src: "$testProjectDir/build/libs/$TEST_PROJECT_NAME-1.0.jar", dest: unjarPath)
        assert new File("$unjarPath/META-INF/MANIFEST.MF").text.contains('myprop: myvalue')
        assert new File("$unjarPath/META-INF/myfile").isFile()

        // Build docs
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'javadoc', 'groovydoc').run()
        new TestFile(testProjectDir, 'build/docs/javadoc/index.html').assertExists()
        new TestFile(testProjectDir, 'build/docs/groovydoc/index.html').assertExists()

        // This test is also important for test cleanup
        executer.inDirectory(groovyProjectDir).withTasks('clean').run()
        new TestFile(testProjectDir, "build").assertDoesNotExist()
    }

    @Test
    public void groovyProjectQuickstartSample() {
        File groovyProjectDir = new File(dist.samplesDir, 'groovy/quickstart')
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'libs').run()

        // Check tests have run
        new TestFile(groovyProjectDir, 'build/test-results/TEST-org.gradle.PersonTest.xml').assertExists()
        new TestFile(groovyProjectDir, 'build/test-results/TESTS-TestSuites.xml').assertExists()

        // Check jar exists
        new TestFile(groovyProjectDir, "build/libs/quickstart-unspecified.jar").assertExists()
    }

    @Test
    public void groovy1_5_6Sample() {
        File groovyProjectDir = new File(dist.samplesDir, 'groovy/groovy-1.5.6')
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'libs').run()
        
        // Check tests have run
        new TestFile(groovyProjectDir, 'build/test-results/TEST-org.gradle.PersonTest.xml').assertExists()
        new TestFile(groovyProjectDir, 'build/test-results/TESTS-TestSuites.xml').assertExists()

        // Check jar exists
        new TestFile(groovyProjectDir, "build/libs/groovy-1.5.6-unspecified.jar").assertExists()
    }
}
