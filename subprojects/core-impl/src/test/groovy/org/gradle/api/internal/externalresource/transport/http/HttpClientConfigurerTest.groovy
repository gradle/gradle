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
package org.gradle.api.internal.externalresource.transport.http

import org.apache.http.auth.AuthScope
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpProtocolParams
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.resource.UriResource
import spock.lang.Specification

public class HttpClientConfigurerTest extends Specification {
    DefaultHttpClient httpClient = new DefaultHttpClient()
    PasswordCredentials credentials = Mock()
    HttpSettings httpSettings = Mock()
    HttpProxySettings proxySettings = Mock()
    HttpClientConfigurer configurer = new HttpClientConfigurer(httpSettings)

    def "configures http client with no credentials or proxy"() {
        httpSettings.credentials >> credentials
        httpSettings.proxySettings >> proxySettings

        when:
        configurer.configure(httpClient)

        then:
        !httpClient.getHttpRequestRetryHandler().retryRequest(new IOException(), 1, null)
    }

    def "configures http client with proxy credentials"() {
        httpSettings.credentials >> credentials
        httpSettings.proxySettings >> proxySettings
        proxySettings.proxy >> new HttpProxySettings.HttpProxy("host", 1111, "domain/proxyUser", "proxyPass")

        when:
        configurer.configure(httpClient)

        then:
        def proxyCredentials = httpClient.getCredentialsProvider().getCredentials(new AuthScope("host", 1111))
        proxyCredentials.userPrincipal.name == "domain/proxyUser"
        proxyCredentials.password == "proxyPass"

        and:
        def ntlmCredentials = httpClient.getCredentialsProvider().getCredentials(new AuthScope("host", 1111, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN/proxyUser'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'proxyUser'
        ntlmCredentials.password == 'proxyPass'
        ntlmCredentials.workstation != ''
    }

    def "configures http client with credentials"() {
        httpSettings.credentials >> credentials
        credentials.username >> "domain/user"
        credentials.password >> "pass"
        httpSettings.proxySettings >> proxySettings

        when:
        configurer.configure(httpClient)

        then:
        def basicCredentials = httpClient.getCredentialsProvider().getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT))
        basicCredentials.userPrincipal.name == "domain/user"
        basicCredentials.password == "pass"

        and:
        def ntlmCredentials = httpClient.getCredentialsProvider().getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN/user'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'user'
        ntlmCredentials.password == 'pass'
        ntlmCredentials.workstation != ''

        and:
        httpClient.getRequestInterceptor(0) instanceof HttpClientConfigurer.PreemptiveAuth
    }

    def "configures http client with user agent"() {
        httpSettings.credentials >> credentials
        httpSettings.proxySettings >> proxySettings

        when:
        configurer.configure(httpClient)

        then:
        HttpProtocolParams.getUserAgent(httpClient.params) == UriResource.userAgentString
    }
}
