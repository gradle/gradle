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
            .deprecations(AndroidDeprecations) {
                maybeExpectIsPropertyDeprecationWarnings()
            }
            .build()
    }

}
