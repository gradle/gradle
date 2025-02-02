/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration.fixture

import groovy.transform.SelfType
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults
import org.gradle.internal.jvm.Jvm
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatformFactory
import org.gradle.platform.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GUtil

import java.util.stream.Collectors
import java.util.stream.Stream

import static org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesUtils.getToolchainUrlPropertyForPlatform

@SelfType(AbstractIntegrationSpec)
trait DaemonJvmPropertiesFixture {
    void assertDaemonUsedJvm(Jvm expectedJvm) {
        assertDaemonUsedJvm(expectedJvm.javaHome)
    }

    void assertDaemonUsedJvm(File expectedJavaHome) {
        assert file("javaHome.txt").text == expectedJavaHome.canonicalPath
    }

    void captureJavaHome() {
        buildFile << """
            def javaHome = org.gradle.internal.jvm.Jvm.current().javaHome.canonicalPath
            println javaHome
            file("javaHome.txt").text = javaHome
        """
    }

    TestFile getDaemonJvmPropertiesFile() {
        return file(DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE)
    }

    void assertJvmCriteria(JavaVersion version, String vendor = null, String implementation = null) {
        Map<String, String> properties = daemonJvmPropertiesFile.properties
        assert properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY) == version.majorVersion
        if (vendor) {
            assert vendor.equalsIgnoreCase(properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY))
        }
        assert properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY) == implementation
    }

    void assertToolchainDownloadUrlsProperties(Map<List<String>, String> platformToolchainUrl) {
        Map<String, String> properties = daemonJvmPropertiesFile.properties
        platformToolchainUrl.forEach { platform, url ->
            def toolchainUrlProperty = String.format(DaemonJvmPropertiesDefaults.TOOLCHAIN_URL_PROPERTY_FORMAT, platform[0], platform[1])
            assert properties.get(toolchainUrlProperty) == url
        }
    }

    void writeJvmCriteria(Jvm jvm) {
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jvm)
        writeJvmCriteria(jvm.javaVersion, otherMetadata.vendor.rawVendor)
    }

    void writeJvmCriteria(JavaVersion version, String vendor = null, String implementation = null) {
        Properties properties = daemonJvmPropertiesFile.exists() ? GUtil.loadProperties(daemonJvmPropertiesFile) : new Properties()
        properties.put(DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY, version.majorVersion)
        if (vendor) {
            properties.put(DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, vendor)
        }
        if (implementation) {
            properties.put(DaemonJvmPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, implementation)
        }
        daemonJvmPropertiesFile.writeProperties(properties)
        assertJvmCriteria(version, vendor, implementation)
    }

    void writeToolchainDownloadUrls(String url) {
        Properties properties = daemonJvmPropertiesFile.exists() ? GUtil.loadProperties(daemonJvmPropertiesFile) : new Properties()
        Stream.of(Architecture.X86_64, Architecture.AARCH64).flatMap(arch ->
            Stream.of(OperatingSystem.values()).map(os -> BuildPlatformFactory.of(arch, os))).collect(Collectors.toSet()).forEach { buildPlatform ->
            String buildPlatformUrlProperty = getToolchainUrlPropertyForPlatform(buildPlatform)
            properties.put(buildPlatformUrlProperty, url)
        }
        daemonJvmPropertiesFile.writeProperties(properties)
    }
}
