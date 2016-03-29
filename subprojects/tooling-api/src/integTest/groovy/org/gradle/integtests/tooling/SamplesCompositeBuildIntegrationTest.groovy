/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Ignore

class SamplesCompositeBuildIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample sample = new Sample(temporaryFolder)

    @Ignore("Need to improve this so that the current distribution is used for each participant, and it shares daemons with other tests")
    @UsesSample('compositeBuild')
    def "can define composite build and execute task"() {
        given:
        tweakProject()

        when:
        executer.inDirectory(sample.dir)
        succeeds('buildProject')

        then:
        result.assertOutputContains("Running build tasks in target project: project3")
        result.assertOutputContains(":3a:build")
        result.assertOutputContains(":3b:build")
    }

    private void tweakProject(TestFile projectDir = sample.dir) {
        // Inject some additional configuration into the sample build script
        def buildFile = projectDir.file('build.gradle')

        def buildScript = buildFile.text
        buildScript = buildScript.replaceFirst("newGradleConnection\\(\\)", 'newGradleConnection().useGradleUserHomeDir(new File("' + TextUtil.escapeString(executer.gradleUserHomeDir.absolutePath) + '"))')
        // TODO:DAZ Should be using the current gradle version for each participant, but injecting the daemon registry and daemon timeout
        buildScript = buildScript.replaceAll("addParticipant\\((project.)\\)", 'addParticipant($1).useGradleVersion("2.12")')
        buildFile.text = buildScript
    }
}
