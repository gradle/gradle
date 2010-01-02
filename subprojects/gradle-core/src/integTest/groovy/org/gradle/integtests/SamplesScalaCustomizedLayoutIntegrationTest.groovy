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

import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.util.TestFile

@RunWith (DistributionIntegrationTestRunner.class)
class SamplesScalaCustomizedLayoutIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist
    private GradleExecuter executer

    @Test
    public void canBuildJar() {
        TestFile projectDir = dist.samplesDir.file('scala/customizedLayout')

        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        projectDir.file('build/test-results/TEST-org.gradle.sample.impl.PersonImplTest.xml').assertIsFile()
        projectDir.file('build/test-results/TESTS-TestSuites.xml').assertIsFile()

        // Check contents of Jar
        TestFile jarContents = dist.testDir.file('jar')
        projectDir.file("build/libs/customizedLayout.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/sample/api/Person.class',
                'org/gradle/sample/impl/PersonImpl.class'
        )
    }
}