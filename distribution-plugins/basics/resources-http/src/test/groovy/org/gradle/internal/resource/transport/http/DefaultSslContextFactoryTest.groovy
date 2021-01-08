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
import spock.lang.Issue
import spock.lang.Specification
/**
 * Tests loading of keystores and truststores corresponding to system
 * properties specified.
 */
class DefaultSslContextFactoryTest extends Specification {
    def props
    def loader

    void setup() {
        props = ['java.home': System.properties['java.home']]
        loader = new DefaultSslContextFactory.SslContextCacheLoader()
    }

    void 'no properties specified'() {
        when:
        loader.load(props)

        then:
        notThrown(SSLInitializationException)
    }

    void 'non-existent truststore file'() {
        given:
        props['javax.net.ssl.trustStore'] = 'will-not-exist'

        when:
        loader.load(props)

        then:
        thrown(SSLInitializationException)
    }

    void 'valid truststore file without specifying password'() {
        given:
        props['javax.net.ssl.trustStore'] = getDefaultTrustStore()

        when:
        loader.load(props)

        // NOTE: This should not fail, as a password is only necessary for
        //       integrity checking, not accessing trusted public certificates
        //       contained within a keystore
        then:
        notThrown(SSLInitializationException)
    }

    void 'valid truststore file with incorrect password'() {
        given:
        props['javax.net.ssl.trustStore'] = getDefaultTrustStore()
        props['javax.net.ssl.trustStorePassword'] = 'totally-wrong'

        when:
        loader.load(props)

        then:
        thrown(SSLInitializationException)
    }

    void 'valid truststore file with correct password'() {
        given:
        props['javax.net.ssl.trustStore'] = getDefaultTrustStore()
        props['javax.net.ssl.trustStorePassword'] = 'changeit'

        when:
        loader.load(props)

        then:
        notThrown(SSLInitializationException)
    }

    void 'non-existent keystore file'() {
        given:
        props['javax.net.ssl.keyStore'] = 'will-not-exist'

        when:
        loader.load(props)

        then:
        thrown(SSLInitializationException)
    }

    @Issue("gradle/gradle#7546")
    void 'keystore type without keystore file'() {
        given:
        props['javax.net.ssl.keyStoreType'] = 'JKS'

        when:
        loader.load(props)

        then:
        notThrown(SSLInitializationException)
    }

    void 'keystore type with NONE keystore file'() {
        given:
        props['javax.net.ssl.keyStoreType'] = 'JKS'
        props['javax.net.ssl.keyStore'] = 'NONE'

        when:
        loader.load(props)

        then:
        notThrown(SSLInitializationException)
    }

    void 'valid keystore file without specifying password'() {
        given:
        props['javax.net.ssl.keyStore'] = getDefaultTrustStore()

        when:
        loader.load(props)

        // NOTE: This should fail, as the private keys password must match
        //       the keystore password, so a password is necessary
        then:
        thrown(SSLInitializationException)
    }

    void 'valid keystore file with incorrect password'() {
        given:
        props['javax.net.ssl.keyStore'] = getDefaultTrustStore()
        props['javax.net.ssl.keyStorePassword'] = 'totally-wrong'

        when:
        loader.load(props)

        then:
        thrown(SSLInitializationException)
    }

    void 'valid keystore file with correct password'() {
        given:
        props['javax.net.ssl.keyStore'] = getDefaultTrustStore()
        props['javax.net.ssl.keyStorePassword'] = 'changeit'

        when:
        loader.load(props)

        then:
        notThrown(SSLInitializationException)
    }

    // NOTE: A keystore and a truststore are generally both simply a JKS
    //       file.  A default "keystore" is always shipped with the JRE and
    //       it contains simply trusted public certificates, and it is the
    //       default truststore used when one is not explicitly specified.
    String getDefaultTrustStore() {
        File keyStore = new File(props['java.home'], 'lib/security/jssecacerts')

        if (!keyStore.exists()) {
            keyStore = new File(props['java.home'], 'lib/security/cacerts')
        }

        keyStore.path
    }
}
