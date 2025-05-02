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
package org.gradle.internal.remote.internal.inet

import org.gradle.util.Matchers
import spock.lang.Specification

class SocketInetAddressTest extends Specification {
    def localhost = java.net.InetAddress.getByName("localhost")

    def "has useful display name"() {
        def address = new SocketInetAddress(localhost, 234)

        expect:
        address.displayName == "localhost/${localhost.hostAddress}:234"
        address.toString() == "localhost/${localhost.hostAddress}:234"
    }

    def "equal when address and port are equal"() {
        def address = new SocketInetAddress(localhost, 234)
        def same = new SocketInetAddress(localhost, 234)
        def differentPort = new SocketInetAddress(localhost, 45)
        def differentAddress = new SocketInetAddress(java.net.InetAddress.getByName("192.168.1.1"), 45)

        expect:
        address Matchers.strictlyEqual(same)
        address != differentAddress
        address != differentPort
    }
}
