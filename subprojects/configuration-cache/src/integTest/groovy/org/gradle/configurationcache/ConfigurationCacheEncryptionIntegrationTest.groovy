/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.test.fixtures.file.TestFile

import java.security.KeyStore

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    File keyStorePath
    String keyStorePassword
    String keyPassword

    def setup() {
        keyStorePath = new TestFile(testDirectory, "test.keystore")
        keyStorePassword = "gradle-keystore-pwd"
        keyPassword = "gradle-key-pwd"
    }

    def "configuration cache can be loaded without errors if encryption is #status"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(status)

        when:
        runWithEncryption(status)

        then:
        configurationCache.assertStateLoaded()

        where:
        status << [true, false]
    }

    def "configuration cache is discarded if was encrypted but encryption is off"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)

        when:
        runWithEncryption(false)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because encryption mode has changed.")
    }

    def "configuration cache is discarded if was not encryted but encryption is on"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(false)

        when:
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because encryption mode has changed.")
    }
    def "configuration cache is discarded if keystore password is incorrect"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)

        when:
        keyStorePassword = "foobar"
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because encryption mode has changed.")
    }

    def "configuration cache is discarded if keystore is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)
        keyStorePath.delete()

        when:
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because encryption key has changed.")
    }

    def "configuration cache is discarded if key is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStorePath.withInputStream { ks.load(it, keyStorePassword.toCharArray()) }
        ks.deleteEntry("gradle-secret")
        keyStorePath.withOutputStream { ks.store(it, keyStorePassword.toCharArray()) }

        when:
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because encryption key has changed.")
    }

    def void runWithEncryption(boolean enabled) {
        configurationCacheRun 'help',
            "-Dorg.gradle.configuration-cache.internal.encrypted=$enabled",
            "-Dorg.gradle.configuration-cache.internal.key-store-path=${keyStorePath}",
            "-Dorg.gradle.configuration-cache.internal.key-store-password=${keyStorePassword}",
            "-Dorg.gradle.configuration-cache.internal.key-password=${keyPassword}"
    }
}
