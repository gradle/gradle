/*
 * Copyright 2018 the original author or authors.
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

import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClientBuilder
import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class HttpClientConfigurerIntegrationTest extends Specification {

    @Requires(TestPrecondition.JDK7)
    def 'configures TLSv1.2 protocol with Java 7'() {
        given:
        def settings = DefaultHttpSettings.builder()
                                            .withAuthenticationSettings(Collections.emptyList())
                                            .allowUntrustedConnections().build()
        def builder = new HttpClientBuilder()

        when:
        new HttpClientConfigurer(settings).configure(builder)

        then:
        SSLConnectionSocketFactory socketFactory = builder.sslSocketFactory
        socketFactory.supportedProtocols == ['TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[]
    }
}
