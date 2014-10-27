/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health

import org.gradle.launcher.daemon.protocol.BuildAndStop
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import spock.lang.Specification

class DescribeDaemonHealthTest extends Specification {

    def exec = Mock(DaemonCommandExecution)
    def stats = Mock(DaemonStats)
    def describe = new DescribeDaemonHealth(stats)

    def "describes health"() {
        when: describe.execute(exec)

        then: 1 * stats.buildStarted()
        then: 1 * exec.proceed()
        then: 1 * stats.buildFinished()
        0 * stats._
    }

    def "does not describe health for single use daemon"() {
        when: describe.execute(exec)

        then:
        1 * exec.getCommand() >> Mock(BuildAndStop)
        1 * exec.proceed()
        0 * _
    }
}
