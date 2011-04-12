/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.internal.IntegrationTestHint
import org.junit.Rule
import spock.lang.Specification

class SamplesToolingApiIntegrationTest extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('toolingApi')

    def canUseToolingApiToDetermineProjectClasspath() {
        def projectDir = sample.dir.file('model')
        Properties props = new Properties()
        props['toolingApiRepo'] = distribution.libsRepo.toURI().toString()
        props['gradleDistribution'] = distribution.gradleHomeDir.toString()
        projectDir.file('gradle.properties').withOutputStream {outstr ->
            props.store(outstr, 'props')
        }
        projectDir.file('settings.gradle').text = '// to stop search upwards'

        when:
        def result = run(projectDir, 'run')

        then:
        result.output.contains("gradle-tooling-api-${distribution.version}.jar")
        result.output.contains("gradle-core-${distribution.version}.jar")
        result.output.contains("gradle-wrapper-${distribution.version}.jar")
    }

    def canUseToolingApiToRunABuild() {
        def projectDir = sample.dir.file('build')
        Properties props = new Properties()
        props['toolingApiRepo'] = distribution.libsRepo.toURI().toString()
        props['gradleDistribution'] = distribution.gradleHomeDir.toString()
        projectDir.file('gradle.properties').withOutputStream {outstr ->
            props.store(outstr, 'props')
        }
        projectDir.file('settings.gradle').text = '// to stop search upwards'

        when:
        def result = run(projectDir, 'run')

        then:
        result.output.contains("Welcome to Gradle ${distribution.version}.")
    }

    private ExecutionResult run(dir, task) {
        try {
            return executer.inDirectory(dir).withTasks(task).run()
        } catch (Exception e) {
            throw new IntegrationTestHint(e);
        }
    }
}
