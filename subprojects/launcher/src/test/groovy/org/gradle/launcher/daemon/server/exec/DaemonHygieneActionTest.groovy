/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.daemon.server.exec

import org.gradle.internal.TimeProvider
import spock.lang.Specification


class DaemonHygieneActionTest extends Specification {

    def "performs hygiene action"() {
        def a = Spy(DaemonHygieneAction)

        when:
        a.execute(Mock(DaemonCommandExecution))
        a.execute(Mock(DaemonCommandExecution))

        then:
        1 * a.gc()
    }

    def "does not trigger gc too often"() {
        def timeProvider = Stub(TimeProvider) {
            getCurrentTime() >>> [10, 100, 200]
        }
        def a = Spy(DaemonHygieneAction, constructorArgs: [100, timeProvider])

        when:
        //executed X3
        a.execute(Mock(DaemonCommandExecution))
        a.execute(Mock(DaemonCommandExecution))
        a.execute(Mock(DaemonCommandExecution))

        then:
        //gc() called X2
        2 * a.gc()
    }
}
