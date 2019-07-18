/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.CoreMatchers.containsString

class SamplesMixedJavaAndGroovyIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'groovy/mixedJavaAndGroovy')

    @Before
    void setup() {
        executer.withRepositoryMirrors()
    }

    @Test
    void canBuildJar() {
        TestFile projectDir = sample.dir
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        def result = new DefaultTestExecutionResult(projectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')

        // Check contents of jar
        TestFile tmpDir = file('jarContents')
        projectDir.file('build/libs/mixedJavaAndGroovy-1.0.jar').unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/Person.class',
                'org/gradle/GroovyPerson.class',
                'org/gradle/JavaPerson.class',
                'org/gradle/PersonList.class'
        )
    }

    @Test
    void canBuildDocs() {
        TestFile projectDir = sample.dir
        executer.inDirectory(projectDir).withTasks('clean', 'javadoc', 'groovydoc').run()

        TestFile javadocsDir = projectDir.file("build/docs/javadoc")
        javadocsDir.file("index.html").assertIsFile()
        javadocsDir.file("index.html").assertContents(containsString('mixedJavaAndGroovy 1.0 API'))
        javadocsDir.file("org/gradle/Person.html").assertIsFile()
        javadocsDir.file("org/gradle/JavaPerson.html").assertIsFile()

        TestFile groovydocsDir = projectDir.file("build/docs/groovydoc")
        groovydocsDir.file("index.html").assertIsFile()
        groovydocsDir.file("overview-summary.html").assertContents(containsString('mixedJavaAndGroovy 1.0 API'))
        groovydocsDir.file("org/gradle/JavaPerson.html").assertIsFile()
        groovydocsDir.file("org/gradle/GroovyPerson.html").assertIsFile()
        groovydocsDir.file("org/gradle/PersonList.html").assertIsFile()
    }
}
