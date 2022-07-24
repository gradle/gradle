/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesScalaZincIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule Sample sample = new Sample(temporaryFolder, 'scala/zinc')

    def "can build jar with #dsl dsl"() {
        given:
        def projectDir = sample.dir.file(dsl)

        when:
        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        then:
        // Check contents of Jar
        TestFile jarContents = file('jar')
        projectDir.file("build/libs/zinc.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/sample/Named.class',
                'org/gradle/sample/Person.class'
        )

        where:
        dsl << ['groovy', 'kotlin']
    }
}
