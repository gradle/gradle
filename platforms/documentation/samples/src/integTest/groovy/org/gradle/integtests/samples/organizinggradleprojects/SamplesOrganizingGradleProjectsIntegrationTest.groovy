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
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.junit.Rule

class SamplesOrganizingGradleProjectsIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("organizingGradleProjects/customGradleDistribution")
    def "can build custom gradle distribution"() {
        executer.inDirectory(sample.dir.file('groovy'))

        when:
        succeeds('createCustomGradleDistribution')

        then:
        def customDistribution = sample.dir.file('groovy/build/distributions/mycompany-gradle-4.6-0.1-bin.zip')
        customDistribution.assertExists()
        new ZipTestFixture(customDistribution).assertContainsFile("gradle-4.6/init.d/repositories.gradle")
    }

}
