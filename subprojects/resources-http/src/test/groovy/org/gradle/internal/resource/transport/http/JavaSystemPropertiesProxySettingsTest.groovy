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

package org.gradle.internal.resource.transport.http

import spock.lang.Specification

class JavaSystemPropertiesProxySettingsTest extends Specification {

    def "proxy is not configured when proxyHost property not set"() {
        expect:
        def settings = settings(proxyHost, proxyPort)
        settings.getProxy() == null

        where:
        proxyHost | proxyPort | requestHost
        null      | null      | null
        null      | null      | "foo"
        null      | "111"     | "foo"
        null      | null      | "foo"
        ""        | null      | null
        ""        | ""        | null
        null      | ""        | null
    }

    def "uses specified port property and default port when port property not set or invalid"() {
        expect:
        settings("proxyHost", prop).getProxy().port == value

        where:
        prop     | value
        null     | 80
        ""       | 80
        "notInt" | 80
        "0"      | 0
        "111"    | 111
    }

    def "uses specified proxy user and password"() {
        expect:
        def proxy = new TestSystemProperties("proxyHost", null, user, password).getProxy()
        proxy.credentials?.username == proxyUser
        proxy.credentials?.password == proxyPassword

        where:
        user   | password   | proxyUser | proxyPassword
        "user" | "password" | "user"    | "password"
        "user" | ""         | "user"    | ""
        ""     | "password" | null      | null
        null   | "anything" | null      | null
    }

    private JavaSystemPropertiesProxySettings settings(host, proxyPort) {
        return new TestSystemProperties(host, proxyPort, null, null)
    }

    class TestSystemProperties extends JavaSystemPropertiesProxySettings {
        TestSystemProperties(String proxyHost, String proxyPortString, String proxyUser, String proxyPassword) {
            super('http', 80, proxyHost, proxyPortString, proxyUser, proxyPassword);
        }
    }
}
