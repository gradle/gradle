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

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.stream.Stream

import static org.gradle.configurationcache.EnvironmentKeySource.GRADLE_ENCRYPTION_KEY_ENV_VAR
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    TestFile keyStoreFolder
    TestFile keyStorePath
    String keyStorePassword
    String keyPassword
    String encryptionKey

    def setup() {
        keyStoreFolder = new TestFile(testDirectory, "keystores")
        keyStorePath = new TestFile(keyStoreFolder, "test.keystore")
        keyStorePassword = "gradle-keystore-pwd"
        keyPassword = "gradle-key-pwd"
        encryptionKey = 'qYEI1ig+IFWLxy6/xbYAN8Qg9l9EW0FkB5ZfuwizaX8='
    }

    def "configuration cache can be loaded without errors if encryption is #status, #encryptionTransformation, useEnvVar = #useEnvVar"() {
        given:
        def additionalOpts = [
            "-Dorg.gradle.configuration-cache.internal.encryption-alg=${encryptionTransformation}"
        ]
        def additionalEnvVars = useEnvVar ? [(GRADLE_ENCRYPTION_KEY_ENV_VAR): encryptionKey] : [:]
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(status, ["help"], additionalOpts, additionalEnvVars)

        when:
        runWithEncryption(status, ["help"], additionalOpts, additionalEnvVars)

        then:
        configurationCache.assertStateLoaded()

        where:
        status      | encryptionTransformation  | useEnvVar
        false       | ""                        | true
        true        | "AES/ECB/PKCS5PADDING"    | true
        true        | "AES/CBC/PKCS5PADDING"    | true
        false       | ""                        | false
        true        | "AES/ECB/PKCS5PADDING"    | false
        true        | "AES/CBC/PKCS5PADDING"    | false
    }

    def "configuration cache is #encrypted if strategy=#strategy"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            @${EqualsAndHashCode.name}
            @${ToString.name}
            class SensitiveData {
                String sensitiveField
            }
            class SensitiveTask extends DefaultTask {
                @Internal
                List<Object> values = new ArrayList()
                @Input
                final Property<String> sensitiveInput1 = project.objects.property(String).convention("sensitive_convention")
                @Input
                final Property<String> sensitiveInput2 = project.objects.property(String).convention(sensitiveInput1)
            }
            tasks.register("useSensitive", SensitiveTask) {
                it.values += [
                    "sensitive_value1",
                    new SensitiveData(sensitiveField: "sensitive_value2"),
                    sensitive_property_name,
                    sensitive_property_name2,
                    System.getenv("SENSITIVE_ENV_VAR_NAME")
                ]
                it.sensitiveInput2.set("sensitive_value3")
            }
            tasks.withType(SensitiveTask).configureEach {
                doLast {
                    println("Running \${name}")
                    assert it.values == [
                        "sensitive_value1",
                        new SensitiveData(sensitiveField: "sensitive_value2"),
                        "sensitive_property_value",
                        "sensitive_property_value2",
                        "sensitive_env_var_value"
                    ]
                    assert it.sensitiveInput1.get() == "sensitive_convention"
                    assert it.sensitiveInput2.get() == "sensitive_value3"
                }
            }
        """
        def enabled = strategy.encrypted
        when:
        runWithEncryption(strategy, ["useSensitive"], ["-Psensitive_property_name=sensitive_property_value"], [
            (ENV_PROJECT_PROPERTIES_PREFIX + 'sensitive_property_name2'): 'sensitive_property_value2',
            "SENSITIVE_ENV_VAR_NAME": 'sensitive_env_var_value'
        ])

        then:
        configurationCache.assertStateStored()
        def cacheDir = new File(this.testDirectory, ".gradle/configuration-cache")
        isFoundInDirectory(cacheDir, "sensitive_property_name".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_property_value".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_property_name2".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_property_value2".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_value1".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_value2".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_env_var_value".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_value3".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive_convention".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "sensitive".getBytes()) == !enabled
        isFoundInDirectory(cacheDir, "SENSITIVE".getBytes()) == !enabled
        where:
        encrypted       |   strategy
        "unencrypted"   |   EncryptionStrategy.None
        "encrypted"     |   EncryptionStrategy.Streams
        "encrypted"     |   EncryptionStrategy.Encoding
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

    def "new configuration cache entry if was not encrypted but encryption is on"() {
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

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "encryption disabled if keystore cannot be created"() {
        def fs = NativeServicesTestFixture.instance.get(FileSystem)
        assert keyStoreFolder.mkdir()
        fs.chmod(keyStoreFolder, 0444)
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true)

        then:
        configurationCache.assertStateStored()
        outputContains("Encryption was requested but could not be enabled")
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")

        cleanup:
        fs.chmod(keyStoreFolder, 0666)
    }

    def "encryption disabled if key in env var is invalid"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def invalidEncryptionKey = Base64.encoder.encodeToString("not a valid key".getBytes(StandardCharsets.UTF_8))

        when:
        executer.withStackTraceChecksDisabled()
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_VAR): invalidEncryptionKey])

        then:
        configurationCache.assertStateStored()
        outputContains("Encryption was requested but could not be enabled")
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "new configuration cache entry if env var key changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def differentKey = "O6lTi7qNmAAIookBZGqHqyDph882NPQOXW5P5K2yupM="

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_VAR): this.encryptionKey])

        then:
        configurationCache.assertStateStored()

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_VAR): differentKey])

        then:
        configurationCache.assertStateStored()
    }

    void runWithEncryption(
        boolean enabled,
        List<String> tasks = ["help"],
        List<String> additionalArgs = [],
        Map<String, String> additionalVars = [:]
    ) {
        runWithEncryption(enabled ? EncryptionStrategy.Encoding : EncryptionStrategy.None, tasks, additionalArgs, additionalVars)
    }

    void runWithEncryption(
        EncryptionStrategy strategy,
        List<String> tasks = ["help"],
        List<String> additionalArgs = [],
        Map<String, String> additionalVars = [:]
    ) {
        def args = [
            '-s',
            "-Dorg.gradle.configuration-cache.internal.encryption-strategy=${strategy.id}",
            "-Dorg.gradle.configuration-cache.internal.key-store-path=${keyStorePath}",
            "-Dorg.gradle.configuration-cache.internal.key-store-password=${keyStorePassword}",
            "-Dorg.gradle.configuration-cache.internal.key-password=${keyPassword}"
        ]
        def allArgs = tasks + args + additionalArgs
        executer.withEnvironmentVars(additionalVars)
        configurationCacheRun(*allArgs)
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
