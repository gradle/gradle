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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.junit.Rule

import static org.gradle.util.TestPrecondition.JDK8_OR_EARLIER

class SamplesCodeQualityIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sample = new Sample(temporaryFolder, 'codeQuality')

    @Requires(JDK8_OR_EARLIER)
    def checkReportsGenerated() {
        TestFile projectDir = sample.dir
        TestFile buildDir = projectDir.file('build')

        when:
        executer.inDirectory(projectDir).requireGradleDistribution().withTasks('check').run()

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
    }
}
