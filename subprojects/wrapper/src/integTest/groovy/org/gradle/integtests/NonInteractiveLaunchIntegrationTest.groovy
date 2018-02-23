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

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class NonInteractiveLaunchIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Requires(TestPrecondition.MAC_OS_X)
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
}
