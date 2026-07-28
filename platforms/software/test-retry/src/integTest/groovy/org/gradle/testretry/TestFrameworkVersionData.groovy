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

/**
 * Fixed versions of test frameworks the migrated integration tests spin up in
 * scratch projects. The upstream plugin sourced these from system properties so
 * the plugin's own cross-version test matrix could inject them; the bundled
 * plugin runs against a single Gradle version, so we pin them here.
 */
trait TestFrameworkVersionData {

    static final String JUNIT4_VERSION = "4.13.2"
    static final String JUNIT5_VERSION = "5.12.2"
    static final String JUNIT_PLATFORM_LAUNCHER_VERSION = "1.12.2"
    static final String MOCKITO_VERSION = "5.23.0"
    static final String SPOCK1_VERSION = "1.3-groovy-2.5"
    static final String SPOCK2_VERSION = "2.4-groovy-4.0"
    static final String TESTNG_VERSION = "7.10.2"

    String junit4Dependency() {
        "junit:junit:${JUNIT4_VERSION}"
    }

    String jupiterDependency() {
        "org.junit.jupiter:junit-jupiter:${JUNIT5_VERSION}"
    }

    String jupiterApiDependency() {
        "org.junit.jupiter:junit-jupiter-api:${JUNIT5_VERSION}"
    }

    String jupiterEngineDependency() {
        "org.junit.jupiter:junit-jupiter-engine:${JUNIT5_VERSION}"
    }

    String jupiterParamsDependency() {
        "org.junit.jupiter:junit-jupiter-params:${JUNIT5_VERSION}"
    }

    String junitVintageEngineDependency() {
        "org.junit.vintage:junit-vintage-engine:${JUNIT5_VERSION}"
    }

    String junitPlatformLauncherDependency() {
        "org.junit.platform:junit-platform-launcher:${JUNIT_PLATFORM_LAUNCHER_VERSION}"
    }

    String junitPlatformSuiteEngineDependency() {
        "org.junit.platform:junit-platform-suite-engine:${JUNIT_PLATFORM_LAUNCHER_VERSION}"
    }

    String mockitoDependency() {
        "org.mockito:mockito-core:${MOCKITO_VERSION}"
    }

    String spock1Dependency() {
        "org.spockframework:spock-core:${SPOCK1_VERSION}"
    }

    String spock2Dependency() {
        "org.spockframework:spock-core:${SPOCK2_VERSION}"
    }

    String testNgDependency() {
        "org.testng:testng:${TESTNG_VERSION}"
    }
}
