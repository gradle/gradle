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

import spock.lang.Specification

import static org.gradle.launcher.daemon.server.health.DaemonHealthMessages.lineMatches

class DaemonHealthMessagesTest extends Specification {

    def "knows when line matches health message"() {
        expect:
        lineMatches("Starting build in new daemon [memory: 1 GB]")
        lineMatches("Starting build in new daemon [memory: 1 GB]\n")
        lineMatches("Starting 14th build in daemon [uptime: 4 mins, performance: 90%, memory: 66% of 1 GB]")

        !lineMatches("")
        !lineMatches("foo")
        !lineMatches("Starting build in daemon")
        !lineMatches("build in daemon")
        !lineMatches("build in new daemon")
    }
}
