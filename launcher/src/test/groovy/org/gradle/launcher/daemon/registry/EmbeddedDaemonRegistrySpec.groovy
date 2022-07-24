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
package org.gradle.launcher.daemon.registry

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.internal.remote.Address
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*

class EmbeddedDaemonRegistrySpec extends Specification {

    @Delegate EmbeddedDaemonRegistry registry = new EmbeddedDaemonRegistry()

    def context = [:] as DaemonContext

    def address(value) {
        [key: value] as Address
    }

    def "initially empty"() {
        expect:
        all.empty
        idle.empty
        notIdle.empty
    }

    def "lifecycle"() {
        given:
        store(new DaemonInfo(address(10), context, "password".bytes, Idle))
        store(new DaemonInfo(address(20), context, "password".bytes, Idle))

        expect:
        all.size() == 2
        idle.size() == 2
        notIdle.empty

        when:
        markState(address(10), Busy)

        then:
        all.size() == 2
        idle.size() == 1
        notIdle.size() == 1

        when:
        markState(address(20), Busy)

        then:
        all.size() == 2
        idle.empty
        notIdle.size() == 2

        when:
        markState(address(10), Idle)
        markState(address(20), Idle)

        then:
        all.size() == 2
        idle.size() == 2
        notIdle.empty

        when:
        remove(address(10))
        remove(address(20))

        then:
        all.empty
        idle.empty
        notIdle.empty
    }
}
