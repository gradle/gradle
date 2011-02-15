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
package org.gradle.integtests

import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification
import org.gradle.integtests.fixtures.*

class SamplesApplicationIntegrationTest extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('application')

    def canRunTheApplicationUsingRunTask() {
        when:
        def result = executer.inDirectory(sample.dir).withTasks('run').run()

        then:
        result.output.contains('Greetings from the sample application.')
    }

    def canBuildAndRunTheInstalledApplication() {
        when:
        executer.inDirectory(sample.dir).withTasks('install').run()

        then:
        def installDir = sample.dir.file('build/install')
        installDir.assertIsDir()

        checkApplicationImage(installDir)
    }

    def canBuildAndRunTheZippedDistribution() {
        when:
        executer.inDirectory(sample.dir).withTasks('distZip').run()

        then:
        def distFile = sample.dir.file('build/distributions/application.zip')
        distFile.assertIsFile()

        def installDir = sample.dir.file('unzip')
        distFile.usingNativeTools().unzipTo(installDir)

        checkApplicationImage(installDir)
    }
    
    private void checkApplicationImage(TestFile installDir) {
        installDir.file('application/bin/application').assertIsFile()
        installDir.file('application/bin/application.bat').assertIsFile()
        installDir.file('application/lib/application.jar').assertIsFile()

        def builder = new ScriptExecuter()
        builder.workingDir installDir.file('application/bin')
        builder.executable 'application'
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()

        def result = builder.run()
        result.assertNormalExitValue()

        assert builder.standardOutput.toString().contains('Greetings from the sample application.')
        assert builder.errorOutput.toString() == ''
    }

}
