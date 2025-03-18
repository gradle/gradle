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

plugins {
    java
}

repositories {
    mavenCentral()
    maven(uri("https://repo.gradle.org/gradle/libs-releases"))
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.17")
    implementation("org.gradle:gradle-tooling-api:8.14-milestone-4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    val runHelpExample by registering(JavaExec::class) {
        this.configureExample("help")
    }

    val runTasksExample by registering(JavaExec::class) {
        this.configureExample("tasks")
    }
}

fun JavaExec.configureExample(exampleTaskName: String) {
    mainClass = "TapiClient"
    args("../sample-project", exampleTaskName)

    classpath = configurations.runtimeClasspath.get() + sourceSets.main.get().output
}

