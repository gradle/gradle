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
import org.gradle.messaging.remote.Address
import spock.lang.Specification

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
        busy.empty
    }

    def "lifecycle"() {
        given:
        store(address(10), context, "password", true)
        store(address(20), context, "password", true)

        expect:
        all.size() == 2
        idle.size() == 2
        busy.empty

        when:
        markBusy(address(10))

        then:
        all.size() == 2
        idle.size() == 1
        busy.size() == 1

        when:
        markBusy(address(20))

        then:
        all.size() == 2
        idle.empty
        busy.size() == 2

        when:
        markIdle(address(10))
        markIdle(address(20))

        then:
        all.size() == 2
        idle.size() == 2
        busy.empty

        when:
        remove(address(10))
        remove(address(20))

        then:
        all.empty
        idle.empty
        busy.empty
    }
}