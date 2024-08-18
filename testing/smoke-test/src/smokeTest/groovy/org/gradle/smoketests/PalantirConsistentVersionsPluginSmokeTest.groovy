/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion

@Requires(UnitTestPreconditions.Jdk11OrLater)
class PalantirConsistentVersionsPluginSmokeTest extends AbstractSmokeTest {

    def 'basic functionality'() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("com.palantir.consistent-versions") version "${TestedVersions.palantirConsistentVersions}"
            }
            ${mavenCentralRepository()}
        """

        file("settings.gradle") << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id("java")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("com.google.guava:guava")
            }
        """
        file("versions.props") << "com.google.guava:guava = 17.0"

        when:
        runner('--write-locks')
            .expectLegacyDeprecationWarning("The org.gradle.api.plugins.Convention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
            .expectLegacyDeprecationWarning("The org.gradle.api.plugins.JavaPluginConvention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#java_convention_deprecation")
            // They are doing some weird stuff in an afterEvaluate
            // See: https://github.com/palantir/gradle-consistent-versions/blob/28a604723c936f5c93c6591e144c4a1731d570ad/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L277
            .expectLegacyDeprecationWarning("Adding a configuration directly to the configuration container. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Use a factory method instead. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#adding_to_configuration_container")
            .expectLegacyDeprecationWarning("The compileClasspathCopy configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
            .expectLegacyDeprecationWarning("The runtimeClasspathCopy configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
            .expectLegacyDeprecationWarning("The testCompileClasspathCopy configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
            .expectLegacyDeprecationWarning("The testRuntimeClasspathCopy configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
            .expectLegacyDeprecationWarning("Mutating the dependencies of configuration ':gcvLocks' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
            .expectLegacyDeprecationWarning("Mutating the hierarchy of configuration ':apiElements' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
            .expectLegacyDeprecationWarning("Mutating the hierarchy of configuration ':runtimeElements' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
            .expectLegacyDeprecationWarning("Mutating the hierarchy of configuration ':other:apiElements' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
            .expectLegacyDeprecationWarning("Mutating the hierarchy of configuration ':other:runtimeElements' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
            .build()

        then:
        file("versions.lock").exists()

        when:
        def runner = runner("other:dependencies")
            .expectLegacyDeprecationWarning("The org.gradle.api.plugins.Convention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
            .expectLegacyDeprecationWarning("The org.gradle.api.plugins.JavaPluginConvention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#java_convention_deprecation")
            .expectLegacyDeprecationWarning("Adding a configuration directly to the configuration container. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Use a factory method instead. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#adding_to_configuration_container")

        def result = runner.build()

        then:
        result.output.contains("com.google.guava:guava -> 17.0")
    }
}
