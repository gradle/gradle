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

import com.google.common.util.concurrent.UncheckedExecutionException
import org.apache.http.auth.AuthScope
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.ssl.SSLContexts
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.internal.SystemProperties
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.credentials.DefaultHttpHeaderCredentials
import org.gradle.internal.resource.UriTextResource
import spock.lang.Specification

import javax.net.ssl.SSLContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HttpClientConfigurerTest extends Specification {
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()

    PasswordCredentials credentials = Mock()
    AllSchemesAuthentication basicAuthentication = Mock() {
        getCredentials() >> credentials
    }

    HttpProxySettings proxySettings = Mock()
    HttpProxySettings secureProxySettings = Mock()
    HttpTimeoutSettings timeoutSettings = Mock()
    HttpSettings httpSettings = Mock() {
        getProxySettings() >> proxySettings
        getSecureProxySettings() >> secureProxySettings
        getTimeoutSettings() >> timeoutSettings
    }
    SslContextFactory sslContextFactory = Mock() {
        createSslContext() >> SSLContexts.createDefault()
    }
    HttpClientConfigurer configurer = new HttpClientConfigurer(httpSettings)

    def "forces java.home racecondition"() {
        final sslContextFactory = new DefaultSslContextFactory()
        def goodSSLContext = sslContextFactory.createSslContext()
        AtomicReference<UncheckedExecutionException> exceptionRef = new AtomicReference<>()
        AtomicBoolean endBarrierTimedOut = new AtomicBoolean(false)
        CyclicBarrier startBarrier = new CyclicBarrier(2)
        CountDownLatch endBarrier = new CountDownLatch(1)

        when:
        def thread = new Thread({ ->
            SystemProperties.getInstance().withJavaHome(new File('/foo/bar'), { ->
                startBarrier.await(1, TimeUnit.SECONDS)
                try {
                    // This is expected to fail because of the bad path set above. Does not block with synchronization because in same thread.
                    sslContextFactory.createSslContext()
                } catch(UncheckedExecutionException e) {
                    exceptionRef.set(e)
                }
                // endBarrier must time out in a proper solution, because the main thread blocks in sslContextFactory.createSslContext() below.
                endBarrierTimedOut.set( !endBarrier.await(100, TimeUnit.MILLISECONDS) )
            })
        })
        thread.start()
        startBarrier.await(1, TimeUnit.SECONDS)
        SSLContext interestingSSLContext = null
        try {
            // This should not get the bad path because it cannot be executed simultanously with the "withJavaHome" closure above.
            interestingSSLContext = sslContextFactory.createSslContext()
        } finally {
            endBarrier.countDown()
            thread.join(1000)
        }

        then:
        endBarrierTimedOut.get()
        interestingSSLContext != null
        interestingSSLContext == goodSSLContext
        exceptionRef.get().message ==~ /.*foo.bar.lib.security.cacerts.*/
    }

    def "configures http client with no credentials or proxy"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        httpClientBuilder.credentialsProvider.getCredentials(AuthScope.ANY) == null
    }

    def "configures http client with proxy credentials"() {
        httpSettings.authenticationSettings >> []
        httpSettings.sslContextFactory >> sslContextFactory
        proxySettings.proxy >> new HttpProxySettings.HttpProxy("host", 1111, "domain/proxyUser", "proxyPass")

        when:
        configurer.configure(httpClientBuilder)

        then:
        def proxyCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope("host", 1111))
        proxyCredentials.userPrincipal.name == "domain/proxyUser"
        proxyCredentials.password == "proxyPass"

        and:
        def ntlmCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope("host", 1111, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\proxyUser'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'proxyUser'
        ntlmCredentials.password == 'proxyPass'
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
        def basicCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT))
        basicCredentials.userPrincipal.name == "domain/user"
        basicCredentials.password == "pass"

        and:
        def ntlmCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\user'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'user'
        ntlmCredentials.password == 'pass'
        ntlmCredentials.workstation != ''

        and:
        httpClientBuilder.requestFirst[0] instanceof HttpClientConfigurer.PreemptiveAuth
    }

    def "configures http client with http header auth credentials"() {
        def httpHeaderCredentials = new DefaultHttpHeaderCredentials()
        httpHeaderCredentials.setName("TestHttpHeaderName")
        httpHeaderCredentials.setValue("TestHttpHeaderValue")
        AllSchemesAuthentication httpHeaderAuthentication = Mock() {
            getCredentials() >> httpHeaderCredentials
        }

        httpSettings.authenticationSettings >> [httpHeaderAuthentication]
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)
        HttpClientHttpHeaderCredentials actualHttpHeaderCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT))

        then:
        actualHttpHeaderCredentials.header.name == 'TestHttpHeaderName'
        actualHttpHeaderCredentials.header.value == 'TestHttpHeaderValue'

        and:
        httpClientBuilder.requestFirst[0] instanceof HttpClientConfigurer.PreemptiveAuth
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
        1 * httpSettings.proxySettings >> proxySettings
        1 * httpSettings.sslContextFactory >> sslContextFactory
        1 * timeoutSettings.connectionTimeoutMs >> 10000
        2 * timeoutSettings.socketTimeoutMs >> 30000
        httpClientBuilder.defaultRequestConfig.connectTimeout == 10000
        httpClientBuilder.defaultRequestConfig.socketTimeout == 30000
        httpClientBuilder.defaultSocketConfig.soKeepAlive
    }
}
