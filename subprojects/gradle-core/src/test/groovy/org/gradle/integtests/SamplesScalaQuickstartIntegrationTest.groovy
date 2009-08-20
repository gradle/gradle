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
import static org.junit.Assert.assertTrue

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
    public void quickstartScalaProject() {
        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build', 'uploadArchives').run()

        // Check tests have run
        assertExists(projectDir, 'build/test-results/TEST-org.gradle.sample.impl.PersonImplTest.xml')
        assertExists(projectDir, 'build/test-results/TESTS-TestSuites.xml')

        // Check jar exists
        assertExists(projectDir, "build/libs/quickstart-1.0.jar")

        // Check jar uploaded
        assertExists(projectDir, 'repos/quickstart-1.0.jar')
    }

    @Test
    public void quickstartScalaDoc() {
        executer.inDirectory(projectDir).withTasks('scaladoc').run()

        assertExists(projectDir, 'build/docs/scaladoc/index.html')
        assertTrue(projectDir.file('build/docs/scaladoc/org/gradle/sample/api/Person.html').text.contains("Defines the interface for a person."))
    }

    private static void assertExists(File baseDir, String[] path) {
        new TestFile(baseDir).file(path).assertExists()
    }

}
