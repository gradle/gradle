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
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SamplesJavaOnlyIfIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/onlyif')

    @Before
    void setup() {
        executer.beforeExecute {
            executer.usingInitScript(RepoScriptBlockUtil.createMirrorInitScript())
        }
    }

    /**
     * runs a build 3 times.
     * execute clean dists
     * check worked correctly
     *
     * remove test results
     * execute dists
     * check didn't re-run tests
     *
     * remove class file
     * execute dists
     * check that it re-ran tests
     */
    @Test void testOptimizedBuild() {
        TestFile javaprojectDir = sample.dir

        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'build').run()

        // Check tests have run
        assertExists(javaprojectDir, 'build/test-results/test/TEST-org.gradle.PersonTest.xml')
        assertExists(javaprojectDir, 'build/reports/tests/test/index.html')

        // Check jar exists
        assertExists(javaprojectDir, "build/libs/onlyif.jar")

        // remove test results
        removeFile(javaprojectDir, 'build/test-results/test/TEST-org.gradle.PersonTest.xml')
        removeFile(javaprojectDir, 'build/reports/tests/test/index.html')

        executer.inDirectory(javaprojectDir).withTasks('test').run()

        // assert that tests did not run
        // (since neither compile nor compileTests should have done anything)
        assertDoesNotExist(javaprojectDir, 'build/test-results/test/TEST-org.gradle.PersonTest.xml')
        assertDoesNotExist(javaprojectDir, 'build/reports/tests/test/index.html')

        // remove a compiled class file
        removeFile(javaprojectDir, 'build/classes/java/main/org/gradle/Person.class')

        executer.inDirectory(javaprojectDir).withTasks('test').run()

        // Check tests have run
        assertExists(javaprojectDir, 'build/test-results/test/TEST-org.gradle.PersonTest.xml')
        assertExists(javaprojectDir, 'build/reports/tests/test/index.html')
    }

    private static void assertExists(File baseDir, String path) {
        new TestFile(baseDir).file(path).assertExists()
    }

    private static void assertDoesNotExist(File baseDir, String path) {
        new TestFile(baseDir).file(path).assertDoesNotExist()
    }

    private static void removeFile(File baseDir, String path) {
        TestFile file = new TestFile(baseDir).file(path)
        file.assertExists()
        file.delete()
    }
}
