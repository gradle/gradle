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
package org.gradle.integtests

import groovy.io.FileType
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class WrapperIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Requires(TestPrecondition.MAC_OS_X)
    @ToBeFixedForInstantExecution
    def "can execute from Finder"() {
        given:
        file("build.gradle") << """
task hello {
    doLast {
        file('hello.txt').createNewFile()
    }
}
defaultTasks 'hello'
        """
        file('settings.gradle') << ''
        prepareWrapper()
        def gradlewPath = new File(wrapperExecuter.workingDir as File, 'gradlew').absolutePath

        when:
        // use the open program since that is what Finder uses
        // set the cwd to the testDirectory to reproduce where gradle normally is run from
        new TestFile("/usr/bin/open").execute(["-g", gradlewPath], ['JAVA_OPTS="-Xmx512m"'])

        then:
        ConcurrentTestUtil.poll(60, 1) {
            assert file("hello.txt").exists()
        }
    }

    def "can recover from a broken distribution"() {
        buildFile << "task hello"
        prepareWrapper()
        def gradleUserHome = testDirectory.file('some-custom-user-home')
        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        // We can't use a daemon since on Windows the distribution jars will be kept open by the daemon
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath", "--no-daemon")
        result = executer.withTasks("hello").run()
        then:
        result.assertTaskExecuted(":hello")

        when:
        // Delete important file in distribution
        boolean deletedSomething = false
        gradleUserHome.eachFileRecurse(FileType.FILES) { file ->
            if (file.name.startsWith("gradle-launcher")) {
                deletedSomething |= file.delete()
                println("Deleting " + file)
            }
        }
        and:
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath", "--no-daemon")
        result = executer.withTasks("hello").run()
        then:
        deletedSomething
        result.assertHasErrorOutput("does not appear to contain a Gradle distribution.")
        result.assertTaskExecuted(":hello")
    }
}
