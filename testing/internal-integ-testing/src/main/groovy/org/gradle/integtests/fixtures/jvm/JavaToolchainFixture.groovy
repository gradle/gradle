/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import groovy.transform.SelfType
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile

/**
 * Introduces helper methods to write integration tests using Java toolchains.
 */
@SelfType(AbstractIntegrationSpec)
trait JavaToolchainFixture {

    TestFile configureJavaPluginToolchainVersion(Jvm jvm) {
        buildFile << javaPluginToolchainVersion(jvm)
    }

    TestFile configureJavaPluginToolchainVersion(JvmInstallationMetadata installationMetadata) {
        buildFile << javaPluginToolchainVersion(installationMetadata)
    }

    String javaPluginToolchainVersion(Jvm jvm) {
        return javaPluginToolchainVersion(jvm.javaVersion.majorVersionNumber)
    }

    String javaPluginToolchainVersion(JvmInstallationMetadata installationMetadata) {
        return javaPluginToolchainVersion(installationMetadata.languageVersion.majorVersionNumber)
    }

    String javaPluginToolchainVersion(Integer majorVersion) {
        """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${majorVersion})
                }
            }
        """
    }

    AbstractIntegrationSpec withAutoDetection() {
        executer.withArgument("-Porg.gradle.java.installations.auto-detect=true")
        return this as AbstractIntegrationSpec
    }

    /**
     * Usage:
     * <pre>
     *     def jvm1 = Jvm.current()
     *     def jvm2 = AvailableJavaHomes.getDifferentVersion(jvm1.javaVersion)
     *
     *     when:
     *     withInstallations(jvm1, jvm2).run(":task")
     * </pre>
     */
    AbstractIntegrationSpec withInstallations(Jvm jvm, Jvm... rest) {
        return withInstallations([jvm] + rest.toList())
    }

    AbstractIntegrationSpec withInstallations(List<Jvm> jvms) {
        def installationPaths = jvms.collect { it.javaHome.absolutePath }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this as AbstractIntegrationSpec
    }

    /**
     * Usage:
     * <pre>
     *     def jdk1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
     *     def jdk2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.getDifferentVersion(jdk1.languageVersion))
     *
     *     when:
     *     withInstallations(jdk1, jdk2).run(":task")
     * </pre>
     */
    AbstractIntegrationSpec withInstallations(JvmInstallationMetadata installationMetadata, JvmInstallationMetadata... rest) {
        def installationPaths = ([installationMetadata] + rest.toList()).collect { it.javaHome.toAbsolutePath().toString() }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this as AbstractIntegrationSpec
    }

    /**
     * Returns the Java version from the compiled class bytecode.
     */
    JavaVersion classJavaVersion(File classFile) {
        assert classFile.exists()
        return JavaVersion.forClass(classFile.bytes)
    }
}
