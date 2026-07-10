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
package org.gradle.internal.resource.transport.http.ntlm


import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

public class NTLMCredentialsTest extends Specification {

    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    def "uses domain when encoded in username"() {
        def username = "domain\\username"
        def password = "password"

        when:
        def ntlmCredentials = new NTLMCredentials(username, password)

        then:
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.username == 'username'
        ntlmCredentials.password == 'password'
    }

    def "uses domain when encoded in username with forward slash"() {
        def username = "domain/username"
        def password = "password"

        when:
        def ntlmCredentials = new NTLMCredentials(username, password)

        then:
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.username == 'username'
        ntlmCredentials.password == 'password'
    }

    def "uses default domain when not encoded in username"() {
        def username = "username"
        def password = "password"

        when:
        def ntlmCredentials = new NTLMCredentials(username, password)

        then:
        ntlmCredentials.domain == ''
        ntlmCredentials.username == 'username'
        ntlmCredentials.password == 'password'
    }

    def "uses system property for domain when not encoded in username"() {
        System.setProperty("http.auth.ntlm.domain", "domain")
        def username = "username"
        def password = "password"

        when:
        def ntlmCredentials = new NTLMCredentials(username, password)

        then:
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.username == 'username'
        ntlmCredentials.password == 'password'
    }

    def "uses truncated hostname for workstation"() {
        def username = "username"
        def password = "password"

        when:
        def ntlmCredentials = new NTLMCredentials(username, password) {
            protected String getHostName() {
                return "hostname.domain.org"
            }
        }

        then:
        ntlmCredentials.workstation == 'HOSTNAME'
    }

    def "null username passed"() {
        def username = null
        def password = "password"
        when:
        def ntlmCredentials = new NTLMCredentials(username, password)
        then:
        def ex = thrown(NullPointerException)
        ex.message == 'Username must not be null!'
    }
}
