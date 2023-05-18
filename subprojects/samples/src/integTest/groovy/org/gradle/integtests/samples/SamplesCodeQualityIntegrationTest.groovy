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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

class SamplesCodeQualityIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample('codeQuality/codeQuality')
    @Requires([UnitTestPreconditions.StableGroovy, UnitTestPreconditions.Jdk11OrLater]) // FIXME KM temporarily disabling while CodeNarc runs in Worker API with multiple Groovy runtimes
    def "can generate reports with #dsl dsl"() {
        TestFile projectDir = sample.dir.file(dsl)
        TestFile buildDir = projectDir.file('build')

        when:
        executer
            .inDirectory(projectDir)
            .withTasks('check')
            .run()

        then:
        buildDir.file('reports/checkstyle/main.xml').assertDoesNotExist()
        buildDir.file('reports/checkstyle/main.html').assertIsFile()
        buildDir.file('reports/codenarc/main.html').assertIsFile()
        buildDir.file('reports/codenarc/test.html').assertIsFile()
        buildDir.file('reports/pmd/main.html').assertIsFile()
        buildDir.file('reports/pmd/main.xml').assertIsFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
}
