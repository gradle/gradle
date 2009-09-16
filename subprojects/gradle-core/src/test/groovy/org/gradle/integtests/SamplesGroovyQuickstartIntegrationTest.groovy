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

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DistributionIntegrationTestRunner.class)
class SamplesGroovyQuickstartIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void groovyProjectQuickstartSample() {
        TestFile groovyProjectDir = dist.samplesDir.file('groovy/quickstart')
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'build').run()

        // Check tests have run
        groovyProjectDir.file('build/test-results/TEST-org.gradle.PersonTest.xml').assertIsFile()
        groovyProjectDir.file('build/test-results/TESTS-TestSuites.xml').assertIsFile()

        // Check contents of jar
        TestFile tmpDir = dist.testDir.file('jarContents')
        groovyProjectDir.file('build/libs/quickstart-unspecified.jar').unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/Person.class'
        )
    }
}