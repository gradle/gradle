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
package org.gradle.testretry

trait TestFrameworkVersionData {

    String junit4Dependency() {
        "junit:junit:" + System.getProperty("junit4Version")
    }

    String jupiterDependency() {
        "org.junit.jupiter:junit-jupiter:" + System.getProperty("junit5Version")
    }

    String jupiterApiDependency() {
        "org.junit.jupiter:junit-jupiter-api:" + System.getProperty("junit5Version")
    }

    String jupiterEngineDependency() {
        "org.junit.jupiter:junit-jupiter-engine:" + System.getProperty("junit5Version")
    }

    String jupiterParamsDependency() {
        "org.junit.jupiter:junit-jupiter-params:" + System.getProperty("junit5Version")
    }

    String junitVintageEngineDependency() {
        "org.junit.vintage:junit-vintage-engine:" + System.getProperty("junit5Version")
    }

    String junitPlatformLauncherDependency() {
        "org.junit.platform:junit-platform-launcher:" + System.getProperty("junitPlatformLauncherVersion")
    }

    String junitPlatformSuiteEngineDependency() {
        "org.junit.platform:junit-platform-suite-engine:" + System.getProperty("junitPlatformLauncherVersion")
    }

    String mockitoDependency() {
        "org.mockito:mockito-core:" + System.getProperty("mockitoVersion")
    }

    String spock1Dependency() {
        "org.spockframework:spock-core:" + System.getProperty("spock1Version")
    }

    String spock2Dependency() {
        "org.spockframework:spock-core:" + System.getProperty("spock2Version")
    }

    String testNgDependency() {
        "org.testng:testng:" + System.getProperty("testNgVersion")
    }
}
