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
package org.gradle.launcher.daemon

import spock.lang.Specification
import org.gradle.api.GradleException

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
class DaemonTimeoutTest extends Specification {

    def "turns timeout into system property string"() {
        expect:
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout(null, 1000).toSysArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dfoo=bar", 1000).toSysArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=foo", 1000).toSysArg()
        "-Dorg.gradle.daemon.idletimeout=2000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=2000", 1000).toSysArg()
    }

    def "reads and validates timeout"() {
        expect:
        4000 == new DaemonTimeout(['org.gradle.daemon.idletimeout': '4000']).idleTimeout

        when:
        new DaemonTimeout(['org.gradle.daemon.idletimeout': 'asdf']).idleTimeout

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'

        when:
        new DaemonTimeout([:]).idleTimeout

        then:
        ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
    }
}
