/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.plugins.quality


import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.internal.jvm.Jvm
import org.gradle.quality.integtest.fixtures.PmdCoverage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber
import org.junit.Rule

import static org.junit.Assume.assumeTrue

class SamplesCodeQualityIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample('codeQuality/codeQuality')
    @Requires([UnitTestPreconditions.StableGroovy, UnitTestPreconditions.Jdk11OrLater])
    def "can generate reports with #dsl dsl"() {
        assumeTrue(PmdCoverage.supportsJdkVersion(VersionNumber.parse(PmdPlugin.DEFAULT_PMD_VERSION), Jvm.current().javaVersionMajor))

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
