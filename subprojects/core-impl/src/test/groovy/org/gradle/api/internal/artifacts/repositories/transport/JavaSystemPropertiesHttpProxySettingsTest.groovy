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
package org.gradle.api.internal.artifacts.repositories.transport;

import spock.lang.Specification;

class JavaSystemPropertiesHttpProxySettingsTest extends Specification {
    def "proxy is not configured when proxyHost property not set"() {
        expect:
        def settings = new JavaSystemPropertiesHttpProxySettings(null, proxyPort, nonProxyHosts)
        !settings.isProxyConfigured(host)

        where:
        proxyPort | nonProxyHosts | host
        null      | null          | null
        null      | null          | "foo"
        "111"     | null          | "foo"
        null      | "foo|bar|baz" | "foo"
    }

    def "proxy is not configured when host is in list of nonproxy hosts"() {
        expect:
        new JavaSystemPropertiesHttpProxySettings("proxyHost", "111", nonProxyHosts).isProxyConfigured(host) == isProxyConfigured

        where:
        nonProxyHosts | host     | isProxyConfigured
        null          | "foo"    | true
        ""            | "foo"    | true
        "bar"         | "foo"    | true
        "foo"         | "foo"    | false
        "foo|bar|baz" | "foo"    | false
    }

    def "uses specified port property and default port when port property not set or invalid"() {
        expect:
        new JavaSystemPropertiesHttpProxySettings("proxyHost", prop, null).proxyPort == value

        where:
        prop     | value
        null     | 80
        ""       | 80
        "notInt" | 80
        "0"      | 0
        "111"    | 111
    }
}
