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
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ScriptExecuter
import org.gradle.util.TestFile
import org.junit.Rule

class SamplesApplicationIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample('application')

    def canRunTheApplicationUsingRunTask() {
        when:
        def result = executer.inDirectory(sample.dir).withTasks('run').run()

        then:
        result.output.contains('Greetings from the sample application.')
    }

    def canBuildAndRunTheInstalledApplication() {
        when:
        executer.inDirectory(sample.dir).withTasks('installApp').run()

        then:
        def installDir = sample.dir.file('build/install/application')
        installDir.assertIsDir()

        checkApplicationImage(installDir)
    }

    def canBuildAndRunTheZippedDistribution() {
        when:
        executer.inDirectory(sample.dir).withTasks('distZip').run()

        then:
        def distFile = sample.dir.file('build/distributions/application-1.0.2.zip')
        distFile.assertIsFile()

        def installDir = sample.dir.file('unzip')
        distFile.usingNativeTools().unzipTo(installDir)

        checkApplicationImage(installDir.file('application-1.0.2'))
    }
    
    private void checkApplicationImage(TestFile installDir) {
        installDir.file('bin/application').assertIsFile()
        installDir.file('bin/application.bat').assertIsFile()
        installDir.file('lib/application-1.0.2.jar').assertIsFile()
        installDir.file('lib/commons-collections-3.2.1.jar').assertIsFile()

        installDir.file('LICENSE').assertIsFile()
        installDir.file('docs/readme.txt').assertIsFile()

        def builder = new ScriptExecuter()
        builder.workingDir installDir.file('bin')
        builder.executable 'application'
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()

        def result = builder.run()
        result.assertNormalExitValue()

        assert builder.standardOutput.toString().contains('Greetings from the sample application.')
        assert builder.errorOutput.toString() == ''
    }

}
