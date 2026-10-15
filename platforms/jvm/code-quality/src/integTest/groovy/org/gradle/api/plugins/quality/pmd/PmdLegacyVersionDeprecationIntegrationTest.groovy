/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

import org.gradle.api.plugins.quality.PmdPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.quality.integtest.fixtures.PmdCoverage
import org.gradle.util.internal.VersionNumber

import static org.junit.Assume.assumeTrue

class PmdLegacyVersionDeprecationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id("java")
                id("pmd")
            }

            ${mavenCentralRepository()}
        """
        file("src/main/java/org/gradle/Class1.java") << "package org.gradle; class Class1 { }"
    }

    def "emits deprecation and runs PMD when the PMD version cannot be determined"() {
        assumeTrue(PmdCoverage.supportsJdkVersion(VersionNumber.parse(PmdPlugin.DEFAULT_PMD_VERSION), Jvm.current().javaVersionMajor))
        // Gradle reads the PMD version from the Maven metadata in the PMD core jar.
        // Repackage that jar without META-INF/maven so the version cannot be determined.
        buildFile << """
            configurations {
                pmdOriginal
            }

            dependencies {
                ${PmdPlugin.calculateDefaultDependencyNotation(PmdPlugin.DEFAULT_PMD_VERSION).collect { "pmdOriginal(\"$it\")" }.join('\n')}
            }

            def pmdOriginalCore = configurations.pmdOriginal.filter { it.name.startsWith("pmd-core-") }
            def pmdCoreWithoutMavenMetadata = tasks.register("pmdCoreWithoutMavenMetadata", Jar) {
                from({ zipTree(pmdOriginalCore.singleFile) }) {
                    exclude("META-INF/maven/**")
                }
                archiveFileName = "repackaged-pmd.jar"
                destinationDirectory = layout.buildDirectory.dir("repackaged")
            }

            tasks.named("pmdMain", Pmd) {
                pmdClasspath = files(pmdCoreWithoutMavenMetadata) + configurations.pmdOriginal.filter { !it.name.startsWith("pmd-core-") }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(
            "Using PMD versions older than 7.0.0 has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Could not determine the PMD version. " +
                "Upgrade to PMD 7.0.0 or newer. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_pmd_older_than_7"
        )
        succeeds("pmdMain")
        file("build/reports/pmd/main.xml").exists()
    }
}
