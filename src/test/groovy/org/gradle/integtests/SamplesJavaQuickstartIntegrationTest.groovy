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

import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith (DistributionIntegrationTestRunner.class)
class SamplesJavaQuickstartIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;
    
    @Test
    public void quickstartJavaProject() {
        File javaprojectDir = new File(dist.samplesDir, 'java/quickstart')
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'dists', 'uploadArchives').run()

        // Check tests have run
        assertExists(javaprojectDir, 'build/test-results/TEST-org.gradle.PersonTest.xml')
        assertExists(javaprojectDir, 'build/test-results/TESTS-TestSuites.xml')

        // Check jar exists
        assertExists(javaprojectDir, "build/libs/quickstart-1.0.jar")

        // Check jar uploaded
        assertExists(javaprojectDir, 'repos/quickstart-1.0.jar')
    }

    private static void assertExists(File baseDir, String[] path) {
        new TestFile(baseDir).file(path).assertExists()
    }

}
