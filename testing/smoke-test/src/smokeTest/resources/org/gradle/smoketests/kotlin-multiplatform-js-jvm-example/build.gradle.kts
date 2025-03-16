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

plugins {
    kotlin("multiplatform") version "$kotlinVersion"
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    $enableCssSupportOld
                }
            }

            $enableCssSupportNew
        }
    }

    jvm {
        tasks.withType<Test>() {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        named("jsTest") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        named("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.1")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}
