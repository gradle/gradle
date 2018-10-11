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
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.JDK10_OR_EARLIER
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires([KOTLIN_SCRIPT, JDK10_OR_EARLIER]) // FindBugs does not work on JDK 11
class SamplesCodeQualityIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Unroll
    @UsesSample('codeQuality')
    def "can generate reports with #dsl dsl"() {
        TestFile projectDir = sample.dir.file(dsl)
        TestFile buildDir = projectDir.file('build')

        when:
        executer
            .inDirectory(projectDir)
            .requireGradleDistribution()
            .withTasks('check')
            .expectDeprecationWarnings(2) // jdepend and findbugs are deprecated
            .run()

        then:
        buildDir.file('reports/checkstyle/main.xml').assertDoesNotExist()
        buildDir.file('reports/checkstyle/main.html').assertIsFile()
        buildDir.file('reports/codenarc/main.html').assertIsFile()
        buildDir.file('reports/codenarc/test.html').assertIsFile()
        buildDir.file('reports/findbugs/main.html').assertIsFile()
        buildDir.file('reports/jdepend/main.xml').assertIsFile()
        buildDir.file('reports/jdepend/test.xml').assertIsFile()
        buildDir.file('reports/pmd/main.html').assertIsFile()
        buildDir.file('reports/pmd/main.xml').assertIsFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
}
