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
package org.gradle.internal.resource.transport.http

import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.CredentialsStore
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.ssl.SSLContexts
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.UriTextResource
import org.gradle.util.TestCredentialUtil
import spock.lang.Specification

class HttpClientConfigurerTest extends Specification {
    public static final String REMOTE_HOST = "host"
    public static final int SOME_PORT = 1234
    public static final String PROXY_HOST = "proxy"
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()

    PasswordCredentials credentials = Mock()
    AllSchemesAuthentication basicAuthentication = new AllSchemesAuthentication(credentials)

    HttpProxySettings proxySettings = Mock()
    HttpProxySettings secureProxySettings = Mock()
    HttpTimeoutSettings timeoutSettings = Mock()
    SslContextFactory sslContextFactory = Mock() {
        createSslContext() >> SSLContexts.createDefault()
    }
    HttpSettings httpSettings = Mock() {
        getProxySettings() >> proxySettings
        getSecureProxySettings() >> secureProxySettings
        getTimeoutSettings() >> timeoutSettings
        getSslContextFactory() >> sslContextFactory
    }

    HttpClientConfigurer configurer = new HttpClientConfigurer(httpSettings)

    def setup() {
        basicAuthentication.addHost(REMOTE_HOST, SOME_PORT)
    }

    def "configures http client with no credentials or proxy"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        CredentialsStore credentialsProvider = httpClientBuilder.credentialsProvider as CredentialsStore
        credentialsProvider.getCredentials(new AuthScope(null, null, -1, null, null), null) == null
    }

    def "configures http client with proxy credentials"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory
        proxySettings.proxy >> new HttpProxySettings.HttpProxy(PROXY_HOST, SOME_PORT, "domain/proxyUser", "proxyPass")

        when:
        configurer.configure(httpClientBuilder)

        then:
        CredentialsStore credentialsProvider = httpClientBuilder.credentialsProvider as CredentialsStore
        def proxyCredentials = credentialsProvider.getCredentials(new AuthScope(null, PROXY_HOST, SOME_PORT, null, null), null)
        proxyCredentials.userPrincipal.name == "domain/proxyUser"
        new String(proxyCredentials.password) == "proxyPass"

        and:
        def ntlmCredentials = credentialsProvider.getCredentials(new AuthScope(null, PROXY_HOST, SOME_PORT, null, "ntlm"), null)
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\proxyUser'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'proxyUser'
        new String(ntlmCredentials.password) == 'proxyPass'
        ntlmCredentials.workstation != ''
    }

    def "configures http client with basic auth credentials"() {
        httpSettings.authenticationSettings >> [basicAuthentication]
        credentials.username >> "domain/user"
        credentials.password >> "pass"
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        CredentialsStore credentialsProvider = httpClientBuilder.credentialsProvider as CredentialsStore
        def basicCredentials = credentialsProvider.getCredentials(new AuthScope(null, REMOTE_HOST, SOME_PORT, null, null), null)
        basicCredentials.userPrincipal.name == "domain/user"
        new String(basicCredentials.password) == "pass"

        and:
        def ntlmCredentials = credentialsProvider.getCredentials(new AuthScope(null, REMOTE_HOST, SOME_PORT, null, "ntlm"), null)
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\user'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'user'
        new String(ntlmCredentials.password) == 'pass'
        ntlmCredentials.workstation != ''

        and:
        httpClientBuilder.requestInterceptors[0].interceptor instanceof HttpClientConfigurer.PreemptiveAuth
    }

    def "configures http client with http header auth credentials"() {
        def httpHeaderCredentials = TestCredentialUtil.defaultHttpHeaderCredentials("TestHttpHeaderName", "TestHttpHeaderValue")
        AllSchemesAuthentication httpHeaderAuthentication = new AllSchemesAuthentication(httpHeaderCredentials)
        httpHeaderAuthentication.addHost(REMOTE_HOST, SOME_PORT)

        httpSettings.authenticationSettings >> [httpHeaderAuthentication]
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)
        CredentialsStore credentialsProvider = httpClientBuilder.credentialsProvider as CredentialsStore
        HttpClientHttpHeaderCredentials actualHttpHeaderCredentials = credentialsProvider.getCredentials(new AuthScope(null, REMOTE_HOST, SOME_PORT, null, null), null)

        then:
        actualHttpHeaderCredentials.header.name == 'TestHttpHeaderName'
        actualHttpHeaderCredentials.header.value == 'TestHttpHeaderValue'

        and:
        httpClientBuilder.requestInterceptors[0].interceptor instanceof HttpClientConfigurer.PreemptiveAuth
    }

    def "configures http client with user agent"() {
        httpSettings.authenticationSettings >> []
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        httpClientBuilder.userAgent == UriTextResource.userAgentString
    }

    def "configures http client timeout"() {
        when:
        configurer.configure(httpClientBuilder)

        then:
        1 * httpSettings.authenticationSettings >> []
        2 * httpSettings.proxySettings >> proxySettings
        2 * httpSettings.secureProxySettings >> secureProxySettings
        1 * httpSettings.sslContextFactory >> sslContextFactory
        1 * timeoutSettings.connectionTimeoutMs >> 10000
        2 * timeoutSettings.socketTimeoutMs >> 30000
        httpClientBuilder.defaultRequestConfig.connectTimeout.toMilliseconds() == 10000
        httpClientBuilder.defaultRequestConfig.responseTimeout.toMilliseconds() == 30000
    }

    def "enables expect-continue when proxy credentials are configured"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory
        proxySettings.proxy >> new HttpProxySettings.HttpProxy(PROXY_HOST, SOME_PORT, "proxyUser", "proxyPass")

        when:
        configurer.configure(httpClientBuilder)

        then:
        httpClientBuilder.defaultRequestConfig.expectContinueEnabled
    }

    def "does not enable expect-continue when proxy has no credentials"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory
        proxySettings.proxy >> new HttpProxySettings.HttpProxy(PROXY_HOST, SOME_PORT, "", "")

        when:
        configurer.configure(httpClientBuilder)

        then:
        !httpClientBuilder.defaultRequestConfig.expectContinueEnabled
    }
}
