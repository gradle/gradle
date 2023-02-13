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

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.stream.Stream

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

    def "configuration cache is #encrypted if enabled=#status"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            @groovy.transform.EqualsAndHashCode
            class SensitiveData {
                Object sensitiveField
            }
            class SensitiveTask extends DefaultTask {
                @Internal
                List<Object> values = new ArrayList()
            }
            tasks.register("useSensitive", SensitiveTask) {
                it.values += ["sensitive_value1", new SensitiveData(sensitiveField: "sensitive_value2") ]
                def propertyValue = project["sensitive_property_name"]
                assert propertyValue != null
                it.values += propertyValue
            }
            tasks.withType(SensitiveTask).configureEach {
                doLast {
                    println("Running \${name}")
                    assert it.values == ["sensitive_value1", new SensitiveData(sensitiveField: "sensitive_value2"), "sensitive_property_value" ]
                }
            }
        """

        when:
        runWithEncryption(status, ["useSensitive"], ["-Psensitive_property_name=sensitive_property_value"])

        then:
        configurationCache.assertStateStored()
        def cacheDir = new File(this.testDirectory, ".gradle/configuration-cache")
        isFoundInDirectory(cacheDir, "sensitive_property_name".getBytes()) == !status
        isFoundInDirectory(cacheDir, "sensitive_property_value".getBytes()) == !status
        isFoundInDirectory(cacheDir, "sensitive_value1".getBytes()) == !status
        isFoundInDirectory(cacheDir, "sensitive_value2".getBytes()) == !status

        where:
        encrypted       |   status
        "encrypted"     |   true
        "unencrypted"   |   false
    }

    private boolean isFoundInDirectory(File startDir, byte[] toFind) {
        try (Stream<Path> tree = Files.walk(startDir.toPath(), FileVisitOption.FOLLOW_LINKS)) {
            return tree.filter { it.toFile().file }
                .anyMatch {
                    isSubArray(Files.readAllBytes(it), toFind)
                }
        }
    }

    def "new configuration cache entry if was encrypted but encryption is off"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)

        when:
        runWithEncryption(false)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "new configuration cache entry if was not encryted but encryption is on"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(false)

        when:
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }
    def "new configuration cache entry if keystore password is incorrect"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)

        when:
        keyStorePassword = "foobar"
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "new configuration cache entry if keystore is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)
        keyStorePath.delete()

        when:
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "new configuration cache entry if key is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true)
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStorePath.withInputStream { ks.load(it, keyStorePassword.toCharArray()) }
        ks.deleteEntry("gradle-secret")
        keyStorePath.withOutputStream { ks.store(it, keyStorePassword.toCharArray()) }

        when:
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    void runWithEncryption(boolean enabled, List<String> tasks = ["help"], List<String> additionalArgs = []) {
        def args = [
            '-s',
            "-Dorg.gradle.configuration-cache.internal.encrypted=$enabled",
            "-Dorg.gradle.configuration-cache.internal.key-store-path=${keyStorePath}",
            "-Dorg.gradle.configuration-cache.internal.key-store-password=${keyStorePassword}",
            "-Dorg.gradle.configuration-cache.internal.key-password=${keyPassword}"
        ]
        def allArgs = tasks.toList() + args + additionalArgs.toList()
        configurationCacheRun(*(allArgs.collect(Object::toString)))
    }

    boolean isSubArray(byte[] contents, byte[] toFind) {
        for (int i = 0; i <= (contents.length - toFind.length); i++) {
            if (Arrays.equals(contents, i, i + toFind.length, toFind, 0, toFind.length)) {
                return true
            }
        }
        return false
    }
}
