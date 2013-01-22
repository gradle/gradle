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
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import org.junit.Test

/**
 * @author Hans Dockter
 */

class SamplesJavaCustomizedLayoutIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/customizedLayout')

    @Test
    public void canBuildAndUploadJar() {
        TestFile javaprojectDir = sample.dir
                                                      
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'build', 'uploadArchives').run()

        // Check tests have run
        JUnitXmlTestExecutionResult result = new JUnitXmlTestExecutionResult(javaprojectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')

        // Check jar exists
        javaprojectDir.file('build/libs/customizedLayout.jar').assertIsFile()

        // Check contents of Jar
        TestFile jarContents = file('jar')
        javaprojectDir.file('build/libs/customizedLayout.jar').unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/Person.class'
        )
    }
}