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
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.stream.Stream

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX

class ConfigurationCacheEncryptionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    TestFile keyStoreDir

    def setup() {
        keyStoreDir = new TestFile(testDirectory, 'keystores')
    }

    def "configuration cache can be loaded without errors using #encryptionTransformation"() {
        given:
        def additionalOpts = [
            "-Dorg.gradle.configuration-cache.internal.encryption-alg=${encryptionTransformation}"
        ]
        def configurationCache = newConfigurationCacheFixture()
        runWithEncryption(true, ["help"], additionalOpts)

        when:
        runWithEncryption(true, ["help"], additionalOpts)

        then:
        configurationCache.assertStateLoaded()

        where:
        _ | encryptionTransformation
        _ | "AES/ECB/PKCS5PADDING"
        _ | "AES/CBC/PKCS5PADDING"
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
        encrypted     | enabled
        "encrypted"   | true
        "unencrypted" | false
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
        findKeystoreFile().delete()

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
        def keyStoreFile = findKeystoreFile()

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
        failureDescriptionStartsWith "Error loading encryption key from custom Java keystore at ${keyStoreDir}"

        cleanup:
        fs.chmod(keyStoreDir, 0666)
    }

    void runWithEncryption(
        boolean enabled = true,
        List<String> tasks = ["help"],
        List<String> additionalArgs = [],
        Map<String, String> envVars = [:]
    ) {
        def allArgs = tasks + getEncryptionOptions(enabled) + additionalArgs
        executer.withEnvironmentVars(envVars)
        configurationCacheRun(*allArgs)
    }

    private List<String> getEncryptionOptions(boolean enabled = true) {
        if (!enabled) {
            return [
                "-Dorg.gradle.configuration-cache.internal.encryption=false"
            ]
        }
        return [
            '-s',
            "-Dorg.gradle.configuration-cache.internal.key-store-dir=${keyStoreDir}",
        ]
    }

    private boolean isSubArray(byte[] contents, byte[] toFind) {
        Bytes.indexOf(contents, toFind) >= 0
    }

    private TestFile findKeystoreFile() {
        def keyStoreDirFiles = keyStoreDir.allDescendants()
        def keyStorePath = keyStoreDirFiles.find { it.endsWith('gradle.keystore') }
        assert keyStorePath != null
        keyStoreDir.file(keyStorePath)
    }
}
