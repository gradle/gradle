/*
 * Copyright 2009 the original author or authors.
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

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*

@RunWith (DistributionIntegrationTestRunner.class)
class SamplesScalaQuickstartIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    private TestFile projectDir

    @Before
    void setUp() {
        projectDir = dist.samplesDir.file('scala/quickstart')
    }

    @Test
    public void canBuildJar() {
        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        projectDir.file('build/test-results/TEST-org.gradle.sample.impl.PersonImplTest.xml').assertIsFile()
        projectDir.file('build/test-results/TESTS-TestSuites.xml').assertIsFile()

        // Check contents of Jar
        TestFile jarContents = dist.testDir.file('jar')
        projectDir.file("build/libs/quickstart-unspecified.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/sample/api/Person.class',
                'org/gradle/sample/impl/PersonImpl.class'
        )
    }

    @Test
    public void canBuildScalaDoc() {
        executer.inDirectory(projectDir).withTasks('clean', 'scaladoc').run()

        projectDir.file('build/docs/scaladoc/index.html').assertExists()
        projectDir.file('build/docs/scaladoc/org/gradle/sample/api/Person.html').assertContents(containsString("Defines the interface for a person."))
    }
}
