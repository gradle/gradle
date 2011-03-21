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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.integtests.fixtures.ScriptExecuter
import org.junit.Rule
import spock.lang.Specification
import org.hamcrest.Matchers
import org.gradle.util.TestFile

class ApplicationIntegrationTest extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleExecuter executer = new GradleDistributionExecuter()

    def canUseEnvironmentVariableToPassOptionsToJvmWhenRunningScript() {
        distribution.testFile('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'application'
'''
        distribution.testFile('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (System.getProperty("testValue") == null) {
            throw new RuntimeException("Expected system property not specified");
        }
    }
}
'''

        when:
        executer.withTasks('install').run()

        def builder = new ScriptExecuter()
        builder.workingDir distribution.testDir.file('build/install/application/bin')
        builder.executable "application"
        builder.environment('APPLICATION_OPTS', '-DtestValue=value')

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }

    def canCustomiseTheApplicationName() {
        distribution.testFile('settings.gradle') << 'rootProject.name = "application"'
        distribution.testFile('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'mega-app'
'''
        distribution.testFile('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
    }
}
'''

        when:
        executer.withTasks('install', 'distZip').run()

        then:
        def installDir = distribution.testFile('build/install/mega-app')
        installDir.assertIsDir()
        checkApplicationImage(installDir)

        def distFile = distribution.testFile('build/distributions/mega-app.zip')
        distFile.assertIsFile()

        def distDir = distribution.testFile('build/unzip')
        distFile.usingNativeTools().unzipTo(distDir)
        checkApplicationImage(distDir.file('mega-app'))
    }

    private void checkApplicationImage(TestFile installDir) {
        installDir.file('bin/mega-app').assertIsFile()
        installDir.file('bin/mega-app.bat').assertIsFile()
        installDir.file('lib/application.jar').assertIsFile()

        def builder = new ScriptExecuter()
        builder.workingDir installDir.file('bin')
        builder.executable 'mega-app'
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()

        def result = builder.run()
        result.assertNormalExitValue()
    }

    def installComplainsWhenInstallDirectoryExistsAndDoesNotLookLikeAPreviousInstall() {
        distribution.testFile('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
install.destinationDir = buildDir
'''

        when:
        def result = executer.withTasks('install').runWithFailure()

        then:
        result.assertThatCause(Matchers.startsWith("The specified installation directory '${distribution.testFile('build')}' does not appear to contain an installation"))
    }
}
