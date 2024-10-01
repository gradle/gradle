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

import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion

@Requires(UnitTestPreconditions.Jdk11OrLater)
class PaparazziPluginSmokeTest extends AbstractSmokeTest implements RunnerFactory {

    def setup() {
        AndroidHome.assertIsSet()
    }

    def "basic test execution"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                }
                ${googleRepository()}
            }
        """

        // We are not testing AGP here, just pararazzi. So the AGP version does not really matter.
        def agpVersion = TestedVersions.androidGradle.last()

        buildFile << """
            plugins {
                id("com.android.application") version "${agpVersion}"
                id("app.cash.paparazzi") version "${TestedVersions.paparazzi}"
            }

            ${mavenCentralRepository()}
            ${googleRepository()}

            android {
                compileSdk = 30
                namespace = "org.gradle.android.example.app"
            }
        """

        file("gradle.properties") << """
            android.useAndroidX = true
        """

        file("src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            </manifest>""".stripIndent()

        expect:
        agpRunner(agpVersion, 'testDebug')
            .maybeExpectLegacyDeprecationWarning(
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getCrunchPngs' with the same behavior and mark the old one with @Deprecated, or change the type of 'com.android.build.gradle.internal.dsl.BuildType\$AgpDecorated.isCrunchPngs' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties"
            )
            .maybeExpectLegacyDeprecationWarning(
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getUseProguard' with the same behavior and mark the old one with @Deprecated, or change the type of 'com.android.build.gradle.internal.dsl.BuildType.isUseProguard' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties"
            )
            .maybeExpectLegacyDeprecationWarning(
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getWearAppUnbundled' with the same behavior and mark the old one with @Deprecated, or change the type of 'com.android.build.api.variant.impl.ApplicationVariantImpl.isWearAppUnbundled' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties"
            )
            .build()
    }

}
