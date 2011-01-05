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
package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import org.gradle.integtests.fixtures.Sample

/**
 * @author Hans Dockter
 */
class SamplesCodeQualityIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('codeQuality')

    @Test
    public void checkReportsGenerated() {
        TestFile projectDir = sample.dir
        TestFile buildDir = projectDir.file('build')

        executer.inDirectory(projectDir).withTasks('check').run()

        buildDir.file('checkstyle/main.xml').assertIsFile()
        buildDir.file('reports/codenarc/main.html').assertIsFile()
        buildDir.file('reports/codenarc/test.html').assertIsFile()
    }
}