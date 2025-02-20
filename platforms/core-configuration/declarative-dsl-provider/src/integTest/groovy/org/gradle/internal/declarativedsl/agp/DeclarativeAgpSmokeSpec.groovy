/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.agp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.dsl.GradleDsl
import org.junit.Assume

class DeclarativeAgpSmokeSpec extends AbstractIntegrationSpec {

    /**
     * TODO: no Android version published to the google() repo has the `com.android.ecosystem` plugin yet.
     *   Once there is an AGP version with DCL support published there, remove the hardcoded Maven repo URLs and use {@link org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions}.
     */
    private static final ANDROIDX_DEV_BUILD = "13094289"

    def 'a declarative project configures successfully with AGP'() {
        Assume.assumeTrue("Java version >= 11 required by AGP dependencies", Jvm.current().javaVersionMajor >= 11)

        given:
        file("gradle.properties") << "android.experimental.declarative=true"

        getSettingsFile(GradleDsl.DECLARATIVE) << """
            pluginManagement {
                repositories {
                    google()
                    maven {
                        url = uri("https://androidx.dev/studio/builds/$ANDROIDX_DEV_BUILD/artifacts/artifacts/repository")
                    }
                    mavenCentral()
                }
            }

            plugins {
                id("com.android.ecosystem").version("8.10.0-dev")
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    maven {
                        url = uri("https://androidx.dev/studio/builds/$ANDROIDX_DEV_BUILD/artifacts/artifacts/repository")
                    }
                    mavenCentral()
                }
            }

            include(":lib")


            defaults {
                androidApp {
                    compileSdk = 34
                }

                androidLibrary {
                    compileSdk = 34
                    dependenciesDcl {
                        testImplementation("junit:junit:4.13.2")
                    }
                }
            }
        """

        getBuildFile(GradleDsl.DECLARATIVE, "lib") << """
            androidLibrary {
                namespace = "com.example.lib"
                compileOptions {
                    sourceCompatibility = VERSION_17
                    targetCompatibility = VERSION_17
                }
                defaultConfig {
                    minSdk = 24
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                buildTypes {
                    buildType("debug") {
                        isMinifyEnabled = false
                    }
                    buildType("release") {
                        isMinifyEnabled = true
                    }
                    buildType("staging"){
                        isMinifyEnabled = false
                    }
                }
                lint {
                    abortOnError = false
                    checkReleaseBuilds = false
                }
                dependenciesDcl {
                    implementation("androidx.appcompat:appcompat:1.7.0")
                }
            }
        """

        expect:
        executer.noDeprecationChecks()
        succeeds(":projects", ":tasks", "--all")
        outputContains("lib:assembleStaging")

        /**
         * TODO: consider running an actual build with sources; for that, the Android SDK would be needed as well as some sample source files.
         */
    }
}
