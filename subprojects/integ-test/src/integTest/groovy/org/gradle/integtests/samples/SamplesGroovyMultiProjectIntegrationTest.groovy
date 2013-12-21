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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.containsString

class SamplesGroovyMultiProjectIntegrationTest extends AbstractIntegrationTest {
    static final String TEST_PROJECT_NAME = 'testproject'

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'groovy/multiproject')

    private List mainFiles = ['JavaPerson', 'GroovyPerson', 'GroovyJavaPerson']
    private List excludedFiles = ['ExcludeJava', 'ExcludeGroovy', 'ExcludeGroovyJava']
    private List testFiles = ['JavaPersonTest', 'GroovyPersonTest', 'GroovyJavaPersonTest']

    @Test
    public void groovyProjectSamples() {
        String packagePrefix = 'build/classes/main/org/gradle'
        String testPackagePrefix = 'build/classes/test/org/gradle'


        TestFile groovyProjectDir = sample.dir
        TestFile testProjectDir = groovyProjectDir.file(TEST_PROJECT_NAME)

        executer.inDirectory(groovyProjectDir).withTasks('clean', 'build').run()

        // Check compilation
        mainFiles.each { testProjectDir.file(packagePrefix, it + ".class").assertIsFile() }
        excludedFiles.each { testProjectDir.file(packagePrefix, it + ".class").assertDoesNotExist() }
        testFiles.each { testProjectDir.file(testPackagePrefix, it + ".class").assertIsFile() }

        // The test produce marker files with the name of the test class
        testFiles.each { testProjectDir.file('build', it).assertIsFile() }

        // Check contents of jar
        TestFile tmpDir = file('jarContents')
        testProjectDir.file("build/libs/$TEST_PROJECT_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'META-INF/myfile',
                'org/gradle/main.properties',
                'org/gradle/JavaPerson.class',
                'org/gradle/GroovyPerson.class',
                'org/gradle/GroovyJavaPerson.class'
        )
        tmpDir.file('META-INF/MANIFEST.MF').assertContents(containsString('myprop: myvalue'))

        // Build docs
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'javadoc', 'groovydoc').run()
        testProjectDir.file('build/docs/javadoc/index.html').assertIsFile()
        testProjectDir.file('build/docs/groovydoc/index.html').assertIsFile()
        testProjectDir.file('build/docs/groovydoc/org/gradle/GroovyPerson.html').assertIsFile()
    }
}
