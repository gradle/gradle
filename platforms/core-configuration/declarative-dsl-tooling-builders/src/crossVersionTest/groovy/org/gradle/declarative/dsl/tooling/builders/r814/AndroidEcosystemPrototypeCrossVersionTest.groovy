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

package org.gradle.declarative.dsl.tooling.builders.r814

import org.gradle.declarative.dsl.tooling.builders.AbstractDeclarativeDslToolingModelsCrossVersionTest
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.events.ProgressListener

@TargetGradleVersion(">=8.14")
@ToolingApiVersion('>=8.14')
class AndroidEcosystemPrototypeCrossVersionTest extends AbstractDeclarativeDslToolingModelsCrossVersionTest {

    private static final String ANDROID_ECOSYSTEM_PLUGIN_VERSION = "0.1.40"

    def setup() {
        settingsFile.delete() //we are using a declarative settings file
    }

    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def 'model can be used for evaluation'() {
        given:
        file("settings.gradle.dcl") <<
            """
                pluginManagement {
                    repositories {
                        google() // Needed for the Android plugin, applied by the unified plugin
                        gradlePluginPortal()
                    }
                }

                plugins {
                    id("org.gradle.experimental.android-ecosystem").version("$ANDROID_ECOSYSTEM_PLUGIN_VERSION")
                }

                rootProject.name = "example-android-app"

                defaults {
                    androidApplication {
                        jdkVersion = 17
                        compileSdk = 34
                        minSdk = 30

                        versionCode = 1
                        versionName = "0.1"
                        applicationId = "org.gradle.experimental.android.app"

                        testing {
                            dependencies {
                                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                                runtimeOnly("org.junit.platform:junit-platform-launcher")
                            }
                        }
                    }

                    androidLibrary {
                        jdkVersion = 17
                        compileSdk = 34
                        minSdk = 30

                        testing {
                            dependencies {
                                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                                runtimeOnly("org.junit.platform:junit-platform-launcher")
                            }
                        }

                        buildTypes {
                            release {
                                dependencies {
                                    implementation("com.squareup.okhttp3:okhttp:4.2.2")
                                }

                                defaultProguardFiles = listOf(proguardFile("proguard-android-optimize.txt"))
                                proguardFiles = listOf(proguardFile("proguard-rules.pro"), proguardFile("some_other_file.txt"))

                                minify {
                                    enabled = true
                                }
                            }
                        }
                    }
                }
            """.stripIndent()

        file("app/build.gradle.dcl") <<
            """
                androidApplication {
                    namespace = "org.example.app"
                }
                androidLibrary {
                    secrets {         }
                }
            """.stripIndent()

        when:
        DeclarativeSchemaModel model = fetchSchemaModel(DeclarativeSchemaModel.class)
        SimpleAnalysisEvaluator.@Companion.withSchema(
            model.settingsSequence,
            model.projectSequence
        ).evaluate("settings.gradle.dcl", file("settings.gradle.dcl").text)

        then:
        noExceptionThrown()
    }


    private <T> T fetchSchemaModel(Class<T> modelType, ProgressListener listener = null) {
        toolingApi.withConnection({ connection ->
            def model = connection.model(modelType)
            if (listener != null) {
                model.addProgressListener(listener)
            }
            model.get()
        })
    }
}
