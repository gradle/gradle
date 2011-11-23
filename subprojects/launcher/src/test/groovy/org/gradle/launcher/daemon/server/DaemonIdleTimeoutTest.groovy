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
package org.gradle.launcher.daemon.server

import spock.lang.Specification
import org.gradle.api.GradleException

import static org.gradle.launcher.daemon.server.DaemonIdleTimeout.calculateFromPropertiesOrUseDefault

class DaemonIdleTimeoutTest extends Specification {
    
    def parse(val) {
        calculateFromPropertiesOrUseDefault((DaemonIdleTimeout.SYSTEM_PROPERTY_KEY): val.toString())
    }
    
    def "reads valid timeout"() {
        expect:
        parse(4000) == 4000
    }
    
    def "nice message for invalid"() {
        when:
        parse("asdf")

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }
    
    def "uses default if prop not set"() {
        expect:
        calculateFromPropertiesOrUseDefault("abc": "def") == DaemonIdleTimeout.DEFAULT_IDLE_TIMEOUT
    }

}
