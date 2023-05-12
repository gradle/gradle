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
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.security.KeyStore

/**
 * Tests loading of keystores and truststores corresponding to system
 * properties specified.
 */
class DefaultSslContextFactoryTest extends Specification {
    @TempDir
    File temporaryDir

    @Rule
    SetSystemProperties properties = new SetSystemProperties()

    def factory = new DefaultSslContextFactory()

    void 'no properties specified'() {
        when:
        factory.createSslContext()

        then:
        noExceptionThrown()
    }

    void 'non-existent truststore file'() {
        given:
        System.properties['javax.net.ssl.trustStore'] = 'will-not-exist'

        when:
        factory.createSslContext()

        then:
        Exception exception = thrown()
        exception.cause instanceof SSLInitializationException
        exception.cause.cause instanceof FileNotFoundException
    }

    void 'valid truststore file without specifying password'() {
        given:
        System.properties['javax.net.ssl.trustStore'] = createTrustStore('changeit').absolutePath

        when:
        factory.createSslContext()

        // NOTE: This should not fail, as a password is only necessary for
        //       integrity checking, not accessing trusted public certificates
        //       contained within a keystore
        then:
        noExceptionThrown()
    }

    void 'valid truststore file with incorrect password'() {
        given:
        System.properties['javax.net.ssl.trustStore'] = createTrustStore('changeit').absolutePath
        System.properties['javax.net.ssl.trustStorePassword'] = 'totally-wrong'

        when:
        factory.createSslContext()

        then:
        Exception exception = thrown()
        exception.cause instanceof SSLInitializationException
        exception.cause.message.contains("Integrity check failed")
    }

    void 'valid truststore file with correct password'() {
        given:
        System.properties['javax.net.ssl.trustStore'] = createTrustStore('changeit').absolutePath
        System.properties['javax.net.ssl.trustStorePassword'] = 'changeit'

        when:
        factory.createSslContext()

        then:
        noExceptionThrown()
    }

    void 'non-existent keystore file'() {
        given:
        System.properties['javax.net.ssl.keyStore'] = 'will-not-exist'

        when:
        factory.createSslContext()

        then:
        Exception exception = thrown()
        exception.cause instanceof SSLInitializationException
        exception.cause.cause instanceof FileNotFoundException
    }

    @Issue("gradle/gradle#7546")
    void 'keystore type without keystore file'() {
        given:
        System.properties['javax.net.ssl.keyStoreType'] = 'JKS'

        when:
        factory.createSslContext()

        then:
        noExceptionThrown()
    }

    void 'keystore type with NONE keystore file'() {
        given:
        System.properties['javax.net.ssl.keyStoreType'] = 'JKS'
        System.properties['javax.net.ssl.keyStore'] = 'NONE'

        when:
        factory.createSslContext()

        then:
        noExceptionThrown()
    }

    void 'valid keystore file without specifying password'() {
        given:
        System.properties['javax.net.ssl.keyStore'] = createTrustStore('changeit').absolutePath

        when:
        factory.createSslContext()

        // NOTE: This should fail, as the private keys password must match
        //       the keystore password, so a password is necessary
        then:
        Exception exception = thrown()
        exception.cause instanceof SSLInitializationException
        exception.cause.message.contains("Integrity check failed")
    }

    void 'valid keystore file with incorrect password'() {
        given:
        System.properties['javax.net.ssl.keyStore'] = createTrustStore('changeit').absolutePath
        System.properties['javax.net.ssl.keyStorePassword'] = 'totally-wrong'

        when:
        factory.createSslContext()

        then:
        Exception exception = thrown()
        exception.cause instanceof SSLInitializationException
        exception.cause.message.contains("Integrity check failed")
    }

    void 'valid keystore file with correct password'() {
        given:
        System.properties['javax.net.ssl.keyStore'] = createTrustStore('changeit').absolutePath
        System.properties['javax.net.ssl.keyStorePassword'] = 'changeit'

        when:
        factory.createSslContext()

        then:
        noExceptionThrown()
    }

    File createTrustStore(String password) {
        File trustStore = new File(temporaryDir, "truststore")
        // initialize an empty keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.defaultType)
        keyStore.load(null, null)

        ByteArrayOutputStream out = new ByteArrayOutputStream()
        keyStore.store(out, password.chars)
        trustStore.bytes = out.toByteArray()
        return trustStore
    }
}
