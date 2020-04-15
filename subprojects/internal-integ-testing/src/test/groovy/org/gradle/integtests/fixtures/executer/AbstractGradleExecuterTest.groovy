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

package org.gradle.integtests.fixtures.executer

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class AbstractGradleExecuterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass());

    def gradleDistribution = Mock(GradleDistribution)

    AbstractGradleExecuter executer = new AbstractGradleExecuter(gradleDistribution, testDir) {
        @Override
        protected ExecutionResult doRun() {
            return null
        }

        @Override
        protected ExecutionFailure doRunWithFailure() {
            return null
        }

        @Override
        void assertCanExecute() throws AssertionError {

        }
    }

    def "when requireDaemon is called, arguments should contain --daemon"() {
        when:
        executer.requireDaemon()
        def allArgs = executer.getAllArgs()

        then:
        allArgs.contains("--daemon")
        !allArgs.contains("--no-daemon")
    }

    def "when requireDaemon is not called, arguments should contain --no-daemon"() {
        when:
        def allArgs = executer.getAllArgs()

        then:
        !allArgs.contains("--daemon")
        allArgs.contains("--no-daemon")
    }

    def "when --foreground argument is added, it skips adding --daemon/--no-daemon"() {
        when:
        executer.withArgument("--foreground")
        def allArgs = executer.getAllArgs()

        then:
        !allArgs.contains("--daemon")
        !allArgs.contains("--no-daemon")
    }

    def "when argument is added explicitly, no --daemon argument is added and requireDaemon gets overridden"() {
        when:
        executer.withArgument(argument)
        executer.requireDaemon()
        def allArgs = executer.getAllArgs()

        then:
        !allArgs.contains("--daemon")
        allArgs.contains(argument)

        where:
        argument << ['--no-daemon', '--foreground']
    }
}
