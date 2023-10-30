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

package org.gradle.launcher.daemon.protocol

import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.registry.DaemonStopEvent
import spock.lang.Issue
import spock.lang.Specification

@Issue("GRADLE-3539")
class DaemonStatusAndErrorReportingTest extends Specification {

    def "PID can be null"() {
        given:
        def daemonContext = new DefaultDaemonContext(null, null, null, null, null, null, false, null)

        when:
        def pid = daemonContext.pid;

        then:
        noExceptionThrown()
        pid == null
    }

    def "PID unboxing should not happen in Status"() {
        given:
        Long unknownPID = null;

        when:
        def status = new Status(unknownPID, "", "");

        then:
        notThrown(NullPointerException)
        status.pid == null
    }

    def "PID unboxing should not happen in DaemonStopEvent"() {
        given:
        Long unknownPID = null;

        when:
        def daemonStopEvent = new DaemonStopEvent(null, unknownPID, null, null);

        then:
        notThrown(NullPointerException)
        daemonStopEvent.pid == null
    }
}
