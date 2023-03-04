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

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.configurationcache.EnvironmentVarKeySource.GRADLE_ENCRYPTION_KEY_ENV_KEY
import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    String encryptionKeyText
    String encryptionKeyAsBase64

    def setup() {
        encryptionKeyText = "01234567890123456789012345678901"
        encryptionKeyAsBase64 = Base64.encoder.encodeToString(encryptionKeyText.getBytes(StandardCharsets.UTF_8))
    }

    def "configuration cache can be loaded without errors if encryption is #status, #encryptionTransformation"() {
        given:
        def additionalOpts = [
            "-Dorg.gradle.configuration-cache.internal.encryption-alg=${encryptionTransformation}"
        ]
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(status, ["help"], additionalOpts)

        when:
        runWithEncryption(status, ["help"], additionalOpts)

        then:
        configurationCache.assertStateLoaded()

        where:
        status      | encryptionTransformation
        false       | ""
        true        | "AES/ECB/PKCS5PADDING"
        true        | "AES/CBC/PKCS5PADDING"
    }

    def "configuration cache encryption is #status with option #options"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        runWithEncryption(status, ["help"], ["--info"] + options)

        then:
        configurationCache.assertStateStored()
        status == containsLine(result.output, "Encryption of the configuration cache is enabled.")

        where:
        status  | options
        true    | []
        false   | ["--no-configuration-cache-encryption"]
        true    | ["--configuration-cache-encryption"]
        true    | ["-Dorg.gradle.configuration-cache.encryption=true"]
        false   | ["-Dorg.gradle.configuration-cache.encryption=false"]
        true    | ["--configuration-cache-encryption", "-Dorg.gradle.configuration-cache.encryption=false"]
        false   | ["--no-configuration-cache-encryption", "-Dorg.gradle.configuration-cache.encryption=true"]
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
        runWithEncryption(enabled, ["useSensitive"], ["-Psensitive_property_name=sensitive_property_value"], [
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

    def "encryption disabled if requested but key in env var is not present"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): ""])

        then:
        configurationCache.assertStateStored()
        outputContains("Encryption was requested but could not be enabled")
    }

    def "encryption disabled if requested but key in env var is invalid"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def invalidEncryptionKey = Base64.encoder.encodeToString((encryptionKeyText + "foo").getBytes(StandardCharsets.UTF_8))

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): invalidEncryptionKey])

        then:
        configurationCache.assertStateStored()
        outputContains("Encryption was requested but could not be enabled")
        containsLine(result.output, matchesRegexp(".*java.security.InvalidKeyException.*"))
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "encryption disabled if requested but key is not long enough"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def insufficientlyLongEncryptionKey = Base64.encoder.encodeToString("01234567".getBytes(StandardCharsets.UTF_8))

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): insufficientlyLongEncryptionKey])

        then:
        configurationCache.assertStateStored()
        outputContains("Encryption was requested but could not be enabled")
        containsLine(result.output, matchesRegexp(".*Encryption key length is \\d* bytes, but must be at least \\d* bytes long"))
        outputContains("Calculating task graph as no configuration cache is available for tasks: help")
    }

    def "new configuration cache entry if env var key changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def differentKey = "O6lTi7qNmAAIookBZGqHqyDph882NPQOXW5P5K2yupM="

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): this.encryptionKeyAsBase64])

        then:
        configurationCache.assertStateStored()

        when:
        runWithEncryption(true, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): differentKey])

        then:
        configurationCache.assertStateStored()
    }

    void runWithEncryption(
        boolean enabled,
        List<String> tasks = ["help"],
        List<String> additionalArgs = [],
        Map<String, String> envVars = [:]
    ) {
        def args = [
            "--${enabled ? "" : "no-"}configuration-cache-encryption"
        ]
        def allArgs = tasks + args + additionalArgs
        if (enabled && !envVars.containsKey(GRADLE_ENCRYPTION_KEY_ENV_KEY)) {
            envVars << [(GRADLE_ENCRYPTION_KEY_ENV_KEY): encryptionKeyAsBase64]
        }
        executer.withEnvironmentVars(envVars)
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
