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

plugins {
    id("java-library")
    groovy
    id("gradlebuild.module-identity")
    id("gradlebuild.repositories")
    id("gradlebuild.code-quality")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val testLibs = versionCatalogs.named("testLibs")
dependencies {
    "testImplementation"(platform(testLibs.findLibrary("junitBom").get()))
    "testImplementation"(testLibs.findLibrary("spock").get())
    "testImplementation"(testLibs.findLibrary("spockJUnit4").get())
    "testRuntimeOnly"(testLibs.findLibrary("junitPlatform").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

listOf("quickTest", "platformTest").forEach { taskName ->
    tasks.register(taskName) {
        group = "CI Lifecycle"
        dependsOn("test")
    }
}
