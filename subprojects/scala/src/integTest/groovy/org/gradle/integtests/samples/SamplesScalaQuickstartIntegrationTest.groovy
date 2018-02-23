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
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.containsString

class SamplesScalaQuickstartIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'scala/quickstart')
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, testDirectoryProvider)

    private TestFile projectDir

    @Before
    void setUp() {
        projectDir = sample.dir
    }

    @Test
    public void canBuildJar() {
        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        def result = new DefaultTestExecutionResult(projectDir)
        result.assertTestClassesExecuted('org.gradle.sample.PersonSpec')

        // Check contents of Jar
        TestFile jarContents = file('jar')
        projectDir.file("build/libs/quickstart.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/sample/Named.class',
                'org/gradle/sample/Person.class'
        )
    }

    @Test
    public void canBuildScalaDoc() {
        if (GradleContextualExecuter.isDaemon()) {
            // don't load scala into the daemon as it exhausts permgen
            return
        }

        executer.inDirectory(projectDir).withTasks('clean', 'scaladoc').run()

        projectDir.file('build/docs/scaladoc/index.html').assertExists()
        projectDir.file('build/docs/scaladoc/org/gradle/sample/Named.html')
            .assertContents(containsString("Defines the traits of one who is named."))
    }
}
