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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Jdk9OrLater)
class SamplesApplicationIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample(temporaryFolder, 'java/application')

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can run the application using run task with #dsl dsl"() {
        when:
        executer.inDirectory(sample.dir.file(dsl))
        succeeds('run')

        then:
        outputContains('Greetings from the sample application.')

        where:
        dsl << ['groovy', 'kotlin']
    }

    def "can build and run the installed application with #dsl dsl"() {
        when:
        def dslDir = sample.dir.file(dsl)
        appendExecutableDir(dslDir, executableDir)
        executer.inDirectory(dslDir)
        succeeds('installDist')

        then:
        def installDir = dslDir.file('build/install/my-app')
        installDir.assertIsDir()

        checkApplicationImage(installDir, executableDir)

        where:
        executableDir << ['bin', 'customBin']
        dsl << ['groovy', 'kotlin']
    }

    def "can build and run the zipped distribution with #dsl dsl"() {
        when:
        def dslDir = sample.dir.file(dsl)
        appendExecutableDir(dslDir, executableDir)
        executer.inDirectory(dslDir)
        succeeds('distZip')

        then:
        def distFile = dslDir.file('build/distributions/my-app-1.0.2.zip')
        distFile.assertIsFile()

        def installDir = dslDir.file('unzip')
        distFile.usingNativeTools().unzipTo(installDir)

        checkApplicationImage(installDir.file('my-app-1.0.2'), executableDir)

        where:
        dsl << ['groovy', 'kotlin']
        executableDir << ['bin', 'customBin']
    }

    private void appendExecutableDir(TestFile dslDir, String executableDir) {
        def extension = dslDir.name == 'groovy' ? 'gradle' : 'gradle.kts'
        dslDir.file("build.$extension") << """
application {
    executableDir = "${executableDir}"
}
"""
    }

    private void checkApplicationImage(TestFile installDir, String executableDir) {
        installDir.file("${executableDir}/my-app").assertIsFile()
        installDir.file("${executableDir}/my-app.bat").assertIsFile()
        installDir.file('lib/application-1.0.2.jar').assertIsFile()

        installDir.file('LICENSE').assertIsFile()
        installDir.file('docs/readme.txt').assertIsFile()

        installDir.file("${executableDir}/my-app").text.contains("MODULE_PATH=")
        installDir.file("${executableDir}/my-app.bat").text.contains("MODULE_PATH=")

        def builder = new ScriptExecuter()
        builder.workingDir = installDir.file(executableDir)
        builder.executable = 'my-app'
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()

        def result = builder.run()
        result.assertNormalExitValue()

        assert builder.standardOutput.toString().contains('Greetings from the sample application.')
        assert builder.errorOutput.toString() == ''
    }

}
