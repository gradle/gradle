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

import com.google.common.primitives.Bytes
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.configurationcache.EnvironmentVarKeySource.GRADLE_ENCRYPTION_KEY_ENV_KEY
import static org.gradle.util.Matchers.containsLine

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    String encryptionKeyText
    String encryptionKeyAsBase64

    def setup() {
        encryptionKeyText = "01234567890123456789012345678901"
        encryptionKeyAsBase64 = Base64.encoder.encodeToString(encryptionKeyText.getBytes(StandardCharsets.UTF_8))
    }

    def "configuration cache can be loaded without errors if encryption is #status, #encryptionTransformation"() {
        given:
        List<String> additionalOpts = [
            "-Dorg.gradle.configuration-cache.internal.encryption-alg=${encryptionTransformation}"
        ]
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(status, ["help"] + additionalOpts)

        when:
        runWithEncryption(status, ["help"] + additionalOpts)

        then:
        configurationCache.assertStateLoaded()

        where:
        status      | encryptionTransformation
        false       | ""
        true        | "AES/ECB/PKCS5PADDING"
        true        | "AES/CBC/PKCS5PADDING"
    }

    def "configuration cache enabled #enabled and encryption #encrypted with option #options"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        runWithEncryptionKey(["help", "--info"] + options)

        then:
        if (enabled) {
            configurationCache.assertStateStored()
            assert encrypted == containsLine(result.output, "Encryption of the configuration cache is enabled.")
        } else {
            configurationCache.assertNoConfigurationCache()
        }

        where:
        enabled | encrypted | options
        false   | false     | []
        false   | false     | ["--no-configuration-cache"]
        true    | false     | ["--configuration-cache"]
        true    | false     | ["-Dorg.gradle.configuration-cache=true"]
        true    | false     | ["-Dorg.gradle.unsafe.configuration-cache=true"]
        true    | true      | ["-Dorg.gradle.configuration-cache=true", "-Dorg.gradle.configuration-cache.encryption=true"]
        true    | true      | ["-Dorg.gradle.unsafe.configuration-cache=true", "-Dorg.gradle.configuration-cache.encryption=true"]
        true    | false     | ["-Dorg.gradle.configuration-cache=true", "-Dorg.gradle.configuration-cache.encryption=false"]
        true    | true      | ["-Dorg.gradle.unsafe.configuration-cache=true", "-Dorg.gradle.configuration-cache.encryption=true"]
    }

    def "configuration cache is #encrypted if enabled=#enabled"() {
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
        when:
        runWithEncryption(enabled, ["useSensitive", "-Psensitive_property_name=sensitive_property_value"], [
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
        encrypted       |   enabled
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

    def "build fails if requested but key in env var is not present"() {
        def emptyKey = ""

        given:
        executer.withEnvironmentVars(configureEncryptionKey(emptyKey))

        when:
        fails("help", "--configuration-cache", "--configuration-cache-encryption")

        then:
        result.assertHasErrorOutput """
Error loading encryption key from GRADLE_ENCRYPTION_KEY (environment variable)
> Empty key"""
    }

    def "encryption disabled if requested but key in env var is invalid"() {
        def invalidEncryptionKey = Base64.encoder.encodeToString((encryptionKeyText + "foo").getBytes(StandardCharsets.UTF_8))
        given:
        executer.withEnvironmentVars(configureEncryptionKey(invalidEncryptionKey))


        when:
        fails("help", "--configuration-cache", "--configuration-cache-encryption")

        then:
        result.assertHasErrorOutput """
Error loading encryption key from GRADLE_ENCRYPTION_KEY (environment variable)
> Invalid AES key length: 35 bytes"""
    }

    def "encryption disabled if requested but key is not long enough"() {
        def insufficientlyLongEncryptionKey = Base64.encoder.encodeToString("01234567".getBytes(StandardCharsets.UTF_8))

        given:
        executer.withEnvironmentVars(configureEncryptionKey(insufficientlyLongEncryptionKey))

        when:
        fails("help", "--configuration-cache", "--configuration-cache-encryption")

        then:
        result.assertHasErrorOutput """
Error loading encryption key from GRADLE_ENCRYPTION_KEY (environment variable)
> Encryption key length is 8 bytes, but must be at least 16 bytes long"""

    }

    def "new configuration cache entry if env var key changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def differentKey = "O6lTi7qNmAAIookBZGqHqyDph882NPQOXW5P5K2yupM="

        when:
        runWithEncryption(true, ["help"], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): this.encryptionKeyAsBase64])

        then:
        configurationCache.assertStateStored()

        when:
        runWithEncryption(true, ["help"], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): differentKey])

        then:
        configurationCache.assertStateStored()
    }

    void runWithEncryption(
        boolean enabled,
        List<String> tasks = ["help"],
        Map<String, String> envVars = [:]
    ) {
        def args = [
            "--configuration-cache", enabled ? "--configuration-cache-encryption" : "--no-configuration-cache-encryption"
        ]
        List<String> allArgs = tasks + args
        executer.withEnvironmentVars(enabled ? configureEncryptionKey(encryptionKeyAsBase64, envVars) : envVars)
        run(allArgs.collect { it.toString() } )
    }

    void runWithEncryptionKey(List<String> tasks, String encryptionKey = encryptionKeyAsBase64) {
        executer.withEnvironmentVars(configureEncryptionKey(encryptionKey))
        run(tasks)
    }

    protected Map<String, String> configureEncryptionKey(String keyAsBase64, Map<String, String> envVars = [:]) {
        if (!envVars.containsKey(GRADLE_ENCRYPTION_KEY_ENV_KEY)) {
            envVars << [(GRADLE_ENCRYPTION_KEY_ENV_KEY): keyAsBase64]
        }
        return envVars
    }

    private boolean isSubArray(byte[] contents, byte[] toFind) {
        return Bytes.indexOf(contents, toFind) >= 0
    }
}
