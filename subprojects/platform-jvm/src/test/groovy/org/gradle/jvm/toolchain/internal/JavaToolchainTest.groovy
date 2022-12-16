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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import spock.lang.Specification

import java.nio.file.Paths

class JavaToolchainTest extends Specification {
    def "java version is reported as specified in metadata"() {
        given:
        def javaHome = new File("/jvm/$javaVersion").absoluteFile
        def metadata = JvmInstallationMetadata.from(javaHome, javaVersion, "vendor", "runtimeName", runtimeVersion, "jvmName", jvmVersion, "jvmVendor", "archName")

        when:
        def javaToolchain = new JavaToolchain(metadata, TestFiles.fileFactory(), Mock(JavaToolchainInput) {
            getLanguageVersion() >> JavaLanguageVersion.of(languageVersion)
            getVendor() >> DefaultJvmVendorSpec.any().toString()
            getImplementation() >> JvmImplementation.VENDOR_SPECIFIC.toString()
        }, false)
        then:
        javaToolchain.languageVersion.asInt() == languageVersion
        javaToolchain.javaRuntimeVersion == runtimeVersion
        javaToolchain.jvmVersion == jvmVersion

        where:
        javaVersion | runtimeVersion  | jvmVersion   | languageVersion
        "1.8.0_292" | "1.8.0_292-b10" | "25.292-b10" | 8
        "11.0.11"   | "11.0.9+11"     | "11.0.9+11"  | 11
        "16"        | "16+36"         | "16+36"      | 16
    }

    def "installation metadata identifies whether it is a #description JVM"() {
        def javaHome = new File(javaHomePath).absolutePath
        def metadata = Mock(JvmInstallationMetadata) {
            getJavaHome() >> Paths.get(javaHome)
            getLanguageVersion() >> Jvm.current().javaVersion
        }

        when:
        def javaToolchain = new JavaToolchain(metadata, TestFiles.fileFactory(), Stub(JavaToolchainInput), false)
        def installationMetadata = javaToolchain as JavaInstallationMetadata

        then:
        installationMetadata.installationPath.toString() == javaHome
        installationMetadata.isCurrentJvm() == isCurrentJvm

        where:
        description   | isCurrentJvm | javaHomePath
        "current"     | true         | Jvm.current().javaHome.toString()
        "not current" | false        | "/some/path"
    }
}
