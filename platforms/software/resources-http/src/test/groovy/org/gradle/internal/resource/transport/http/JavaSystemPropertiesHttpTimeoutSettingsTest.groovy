/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.CONNECTION_TIMEOUT_SYSTEM_PROPERTY
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.DEFAULT_CONNECTION_TIMEOUT
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.DEFAULT_IDLE_CONNECTION_TIMEOUT
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.DEFAULT_SOCKET_TIMEOUT
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.IDLE_CONNECTION_TIMEOUT_SYSTEM_PROPERTY
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class JavaSystemPropertiesHttpTimeoutSettingsTest extends Specification {

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties()

    def "can retrieve default values"() {
        JavaSystemPropertiesHttpTimeoutSettings settings = new JavaSystemPropertiesHttpTimeoutSettings()

        expect:
        settings.connectionTimeoutMs == DEFAULT_CONNECTION_TIMEOUT
        settings.socketTimeoutMs == DEFAULT_SOCKET_TIMEOUT
    }

    def "can parse custom value from system property"() {
        System.setProperty(CONNECTION_TIMEOUT_SYSTEM_PROPERTY, "111")
        System.setProperty(SOCKET_TIMEOUT_SYSTEM_PROPERTY, "222")
        System.setProperty(IDLE_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, "333")
        JavaSystemPropertiesHttpTimeoutSettings settings = new JavaSystemPropertiesHttpTimeoutSettings()

        expect:
        verifyAll(settings) {
            connectionTimeoutMs == 111
            socketTimeoutMs == 222
            idleConnectionTimeoutMs == 333
        }
    }

    def "uses default value if provided connection timeout is not valid"() {
        System.setProperty(CONNECTION_TIMEOUT_SYSTEM_PROPERTY, timeout)
        JavaSystemPropertiesHttpTimeoutSettings settings = new JavaSystemPropertiesHttpTimeoutSettings()

        expect:
        settings.connectionTimeoutMs == DEFAULT_CONNECTION_TIMEOUT

        where:
        timeout << ["", "abc"]
    }

    def "uses default value if provided socket timeout is not valid"() {
        System.setProperty(SOCKET_TIMEOUT_SYSTEM_PROPERTY, timeout)
        JavaSystemPropertiesHttpTimeoutSettings settings = new JavaSystemPropertiesHttpTimeoutSettings()

        expect:
        settings.socketTimeoutMs == DEFAULT_SOCKET_TIMEOUT

        where:
        timeout << ["", "abc"]
    }

    def "uses default value if provided idle connection timeout is not valid"() {
        System.setProperty(IDLE_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, timeout)
        JavaSystemPropertiesHttpTimeoutSettings settings = new JavaSystemPropertiesHttpTimeoutSettings()

        expect:
        settings.idleConnectionTimeoutMs == DEFAULT_IDLE_CONNECTION_TIMEOUT

        where:
        timeout << ["", "abc"]
    }
}
