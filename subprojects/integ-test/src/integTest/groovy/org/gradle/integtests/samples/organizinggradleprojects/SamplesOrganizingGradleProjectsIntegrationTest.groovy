/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.organizinggradleprojects

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

class SamplesOrganizingGradleProjectsIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/organizingGradleProjects/customGradleDistribution")
    def "can resolve dependencies"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('createCustomGradleDistribution')

        then:
        sample.dir.file('build/distributions/mycompany-gradle-4.6-bin.zip').isFile()
    }

    @UsesSample("userguide/organizingGradleProjects/separatedTestTypes")
    @Unroll
    def "can execute different types of tests with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('build')

        then:
        nonSkippedTasks.containsAll([':test', ':integTest'])

        where:
        dsl << ['groovy', 'kotlin']
    }
}
