/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Requires
import spock.lang.IgnoreIf

import static org.gradle.util.TestPrecondition.WINDOWS

class DaemonUsageSuggestionIntegrationTest extends AbstractIntegrationSpec {

    public static final String DAEMON_USAGE_SUGGESTION_MESSAGE = "This build could be faster, please consider using the Gradle Daemon"

    def setup() {
        executer.withEnvironmentVars(["CI": ""])
    }

    @IgnoreIf({ GradleContextualExecuter.longLivingProcess || WINDOWS.fulfilled })
    def "prints a suggestion to use the daemon when daemon usage has not been explicitly configured"() {
        when:
        succeeds()

        then:
        output.contains DAEMON_USAGE_SUGGESTION_MESSAGE
    }

    @IgnoreIf({ GradleContextualExecuter.longLivingProcess || WINDOWS.fulfilled })
    def "does not print the suggestion to use the daemon if the daemon is explicitly disabled"() {
        given:
        executer.withArguments("--no-daemon")

        when:
        succeeds()

        then:
        !output.contains(DAEMON_USAGE_SUGGESTION_MESSAGE)
    }

    @IgnoreIf({ !GradleContextualExecuter.longLivingProcess || WINDOWS.fulfilled })
    def "does not print the suggestion to use the daemon when running as part of a long living process"() {
        when:
        succeeds()

        then:
        !output.contains(DAEMON_USAGE_SUGGESTION_MESSAGE)
    }

    @Requires(WINDOWS)
    def "does not print suggestion to use the daemon when on windows even if daemon usage has not been explicitly configured"() {
        when:
        succeeds()

        then:
        !output.contains(DAEMON_USAGE_SUGGESTION_MESSAGE)
    }
}
