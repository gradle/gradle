/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.IgnoreIf
/**
 * For forking a daemon process in embedded mode:
 * The command line length is already close to the limit of what Windows can handle.
 * The few extra arguments that the priority change requires seem to push it over the edge.
 * Therefore, we do not run these tests embedded on Windows.
 **/
@IgnoreIf({ TestPrecondition.doSatisfies(UnitTestPreconditions.Windows) && GradleContextualExecuter.embedded})
class DaemonPriorityIntegrationTest extends DaemonIntegrationSpec {

    def "forks new daemon when priority is set to a different value via command line"() {
        when:
        run("help")

        then:
        daemons.daemons.size() == 1

        when:
        executer.withArguments("--priority", "low")
        run("help")

        then:
        daemons.daemons.size() == 2
    }

    def "forks new daemon when priority is set to a different value via properties"() {
        when:
        run("help")

        then:
        daemons.daemons.size() == 1

        when:
        file("gradle.properties") << "org.gradle.priority=low"
        run("help")

        then:
        daemons.daemons.size() == 2
    }
}
