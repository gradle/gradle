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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule

import static org.hamcrest.CoreMatchers.containsString

class SamplesGroovyMultiProjectIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule public final Sample sample = new Sample(temporaryFolder)
    private final static String TEST_PROJECT_NAME = "testproject"

    @LeaksFileHandles("Workaround till https://github.com/apache/groovy/pull/735 is merged")
    @UsesSample("groovy/multiproject")
    void groovyProjectSamples() {
        def groovySources = ["GroovyPerson", "GroovyJavaPerson"]
        def javaSources = ["JavaPerson"]
        def excludedFiles = ['ExcludeJava', 'ExcludeGroovy', 'ExcludeGroovyJava']

        def testProjectDir = sample.dir.file(TEST_PROJECT_NAME)
        executer.inDirectory(testProjectDir)

        when:
        succeeds("clean", "build")

        then:
        // Check compilation
        groovySources.each {
            testProjectDir.file("build/classes/groovy/main/org/gradle/${it}.class").assertIsFile()
            testProjectDir.file("build/classes/groovy/test/org/gradle/${it}Test.class").assertIsFile()
            testProjectDir.file("build/${it}Test").assertIsFile()
        }
        javaSources.each {
            testProjectDir.file("build/classes/java/main/org/gradle/${it}.class").assertIsFile()
            testProjectDir.file("build/classes/java/test/org/gradle/${it}Test.class").assertIsFile()
            testProjectDir.file("build/${it}Test").assertIsFile()
        }
        excludedFiles.each {
            testProjectDir.file("build/classes/java/main/org/gradle/${it}.class").assertDoesNotExist()
            testProjectDir.file("build/classes/groovy/main/org/gradle/${it}.class").assertDoesNotExist()
        }
        and:
        // Check contents of jar
        def jarFile = testProjectDir.file("build/libs/${TEST_PROJECT_NAME}-1.0.jar")
        jarFile.assertIsFile()
        def jarTestFixture = new JarTestFixture(jarFile)
        jarTestFixture.hasDescendants(
                'META-INF/myfile',
                'org/gradle/main.properties',
                'org/gradle/JavaPerson.class',
                'org/gradle/GroovyPerson.class',
                'org/gradle/GroovyJavaPerson.class'
        )
        jarTestFixture.assertFileContent('META-INF/MANIFEST.MF', containsString('myprop: myvalue'))

        when:
        // Build docs
        executer.inDirectory(testProjectDir)
        succeeds("javadoc", "groovydoc")
        then:
        testProjectDir.file('build/docs/javadoc/index.html').assertIsFile()
        testProjectDir.file('build/docs/groovydoc/index.html').assertIsFile()
        testProjectDir.file('build/docs/groovydoc/org/gradle/GroovyPerson.html').assertIsFile()
    }
}
