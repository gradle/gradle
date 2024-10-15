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

package org.gradle.internal.cc.impl

import com.google.common.primitives.Bytes
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.internal.encryption.impl.EncryptionKind
import org.gradle.internal.encryption.impl.KeyStoreKeySource
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.stream.Stream

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.internal.encryption.impl.EnvironmentVarKeySource.GRADLE_ENCRYPTION_KEY_ENV_KEY
import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    TestFile keyStoreDir
    String encryptionKeyText
    String encryptionKeyAsBase64

    def setup() {
        keyStoreDir = new TestFile(testDirectory, 'keystores')
        encryptionKeyText = "01234567890123456789012345678901"
        encryptionKeyAsBase64 = Base64.encoder.encodeToString(encryptionKeyText.getBytes(StandardCharsets.UTF_8))
    }

    def "configuration cache can be loaded without errors from #source using #encryptionTransformation"() {
        given:
        def additionalOpts = [
            "-Dorg.gradle.configuration-cache.internal.encryption-alg=${encryptionTransformation}"
        ]
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(source, ["help"], additionalOpts)

        when:
        runWithEncryption(source, ["help"], additionalOpts)

        then:
        configurationCache.assertStateLoaded()

        where:
        encryptionTransformation | source
        "AES/CTR/NoPadding"      | EncryptionKind.KEYSTORE
        "AES/CTR/NoPadding"      | EncryptionKind.ENV_VAR
        "AES/GCM/NoPadding"      | EncryptionKind.KEYSTORE
        "AES/GCM/NoPadding"      | EncryptionKind.ENV_VAR
    }

    def "configuration cache encryption enablement is #enabled if kind=#kind"() {
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

        expect:
        findRequiredKeystoreFile(false) == null

        when:
        runWithEncryption(
            kind,
            ["useSensitive"],
            ["-Psensitive_property_name=sensitive_property_value",
             "-Dorg.gradle.configuration-cache.internal.deduplicate-strings=false"],
            [(ENV_PROJECT_PROPERTIES_PREFIX + 'sensitive_property_name2'): 'sensitive_property_value2',
             "SENSITIVE_ENV_VAR_NAME": 'sensitive_env_var_value']
        )

        then:
        configurationCache.assertStateStored()
        enabled == kind.encrypted
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

        (findRequiredKeystoreFile(false) != null) == keystoreExpected

        where:
        kind                    | enabled | keystoreExpected
        EncryptionKind.NONE     | false   | false
        EncryptionKind.KEYSTORE | true    | true
        EncryptionKind.ENV_VAR  | true    | false
    }

    private boolean isFoundInDirectory(File startDir, byte[] toFind) {
        try (Stream<Path> tree = Files.walk(startDir.toPath(), FileVisitOption.FOLLOW_LINKS)) {
            return tree.filter { it.toFile().file }
                .anyMatch {
                    isSubArray(Files.readAllBytes(it), toFind)
                }
        }
    }

    def "new configuration cache entry if keystore is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption()
        findRequiredKeystoreFile().delete()

        when:
        runWithEncryption()

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no cached configuration is available for tasks: help")
    }

    def "new configuration cache entry if key is not found"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption()

        and:
        def keyStoreFile = findRequiredKeystoreFile()

        KeyStore ks = KeyStore.getInstance(KeyStoreKeySource.KEYSTORE_TYPE)
        keyStoreFile.withInputStream { ks.load(it, new char[]{'c', 'c'}) }
        ks.deleteEntry("gradle-secret")
        keyStoreFile.withOutputStream { ks.store(it, new char[]{'c', 'c'}) }

        when:
        runWithEncryption()

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no cached configuration is available for tasks: help")
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "build fails if keystore cannot be created"() {
        given:
        def fs = NativeServicesTestFixture.instance.get(FileSystem)
        assert keyStoreDir.mkdir()
        fs.chmod(keyStoreDir, 0444)

        when:
        fails(*(["help", "--configuration-cache"] + encryptionOptions))

        then:
        failureDescriptionStartsWith "Could not open Gradle Configuration Cache keystore (${keyStoreDir}"

        cleanup:
        fs.chmod(keyStoreDir, 0666)
    }

    void runWithEncryption(
        EncryptionKind kind = EncryptionKind.KEYSTORE,
        List<String> tasks = ["help"],
        List<String> additionalArgs = [],
        Map<String, String> envVars = [:],
        Closure<Void> runner = this::configurationCacheRun
    ) {
        def allArgs = tasks + getEncryptionOptions(kind) + additionalArgs + ["-s"]
        // envVars overrides encryption env vars
        def allVars = getEncryptionEnvVars(kind) + envVars
        executer.withEnvironmentVars(allVars)
        runner(*allArgs)
    }

    private List<String> getEncryptionOptions(EncryptionKind kind = EncryptionKind.KEYSTORE) {
        switch (kind) {
            case EncryptionKind.KEYSTORE:
                return [
                    "-Dorg.gradle.configuration-cache.internal.key-store-dir=${keyStoreDir}",
                ]
            case EncryptionKind.ENV_VAR:
                // the env var is all that is required
                return []
            default:
                // NONE
                return [
                    "-Dorg.gradle.configuration-cache.internal.encryption=false"
                ]
        }
    }

    def "build fails if key is provided via env var but invalid"() {
        given:
        def invalidEncryptionKey = Base64.encoder.encodeToString((encryptionKeyText + "foo").getBytes(StandardCharsets.UTF_8))

        when:
        runWithEncryption(EncryptionKind.ENV_VAR, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): invalidEncryptionKey], this::configurationCacheFails)

        then:
        // since the key is not fully validated until needed, we only get an error when encrypting
        failure.assertHasDescription("Invalid AES key length: 35 bytes")
        // exception error message varies across JCE implementations, but the exception class is predictable
        containsLine(result.error, matchesRegexp(".*java.security.InvalidKeyException.*"))
    }

    def "build fails if key is provided via env var but not Base64-encoded"() {
        given:
        char invalidBase64Char = "!"
        def invalidEncryptionKey = "${invalidBase64Char}${encryptionKeyAsBase64}"

        when:
        runWithEncryption(EncryptionKind.ENV_VAR, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): invalidEncryptionKey], this::configurationCacheFails)

        then:
        // since the key is not fully validated until needed, we only get an error when encrypting
        failure.assertHasDescription("Error loading encryption key from GRADLE_ENCRYPTION_KEY environment variable")
        failure.assertHasCause("Illegal base64 character ${Integer.toHexString((int) invalidBase64Char)}")
    }

    def "build fails if key is provided via env var but not long enough"() {
        given:
        def insufficientlyLongEncryptionKey = Base64.encoder.encodeToString("01234567".getBytes(StandardCharsets.UTF_8))

        when:
        runWithEncryption(EncryptionKind.ENV_VAR, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): insufficientlyLongEncryptionKey], this::configurationCacheFails)

        then:
        failure.assertHasDescription("Error loading encryption key from GRADLE_ENCRYPTION_KEY environment variable")
        failure.assertHasCause("Encryption key length is 8 bytes, but must be at least 16 bytes long")
    }

    def "new configuration cache entry if env var key changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        // Obtained via:
        // openssl enc -aes-128-cbc -P -pbkdf2 -nosalt -k YOUR-OWN-PASSPHRASE-HERE | grep key | cut -f 2 -d = | xxd  -r -ps | base64
        def differentKey = "yqqfx9gxQY0n9W7PQGl/zA=="

        when:
        runWithEncryption(EncryptionKind.ENV_VAR, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): this.encryptionKeyAsBase64])

        then:
        configurationCache.assertStateStored()

        when:
        runWithEncryption(EncryptionKind.ENV_VAR, ["help"], [], [(GRADLE_ENCRYPTION_KEY_ENV_KEY): differentKey])

        then:
        configurationCache.assertStateStored()
    }

    private Map<String, String> getEncryptionEnvVars(EncryptionKind kind = EncryptionKind.KEYSTORE) {
        if (kind == EncryptionKind.ENV_VAR) {
            return [(GRADLE_ENCRYPTION_KEY_ENV_KEY): encryptionKeyAsBase64]
        }
        return [:]
    }

    private boolean isSubArray(byte[] contents, byte[] toFind) {
        Bytes.indexOf(contents, toFind) >= 0
    }

    private TestFile findRequiredKeystoreFile(boolean required = true) {
        def keyStoreDirFiles = keyStoreDir.allDescendants()
        def keyStorePath = keyStoreDirFiles.find { it.endsWith('gradle.keystore') }
        assert !required || keyStorePath != null
        return keyStorePath?.with { keyStoreDir.file(keyStorePath) }
    }
}
