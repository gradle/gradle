/*
 * Copyright 2016 the original author or authors.
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

import org.apache.http.ssl.SSLInitializationException

import org.gradle.internal.SystemProperties

import spock.lang.Specification

/**
 * Tests loading of keystores and truststores corresponding to system
 * properties specified.
 */
class DefaultSslContextFactoryTest extends Specification {
    def sslContextFactory

    void setup() {
        sslContextFactory = new DefaultSslContextFactory()
    }

    void 'creates ssl context without errors'() {
        when:
        def context = sslContextFactory.createSslContext()

        then:
        context != null
        notThrown(SSLInitializationException)
    }
}
