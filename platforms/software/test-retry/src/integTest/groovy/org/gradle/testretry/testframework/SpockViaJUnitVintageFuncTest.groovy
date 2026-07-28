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
package org.gradle.testretry.testframework

import spock.lang.IgnoreIf

@IgnoreIf(
    value = { COMPATIBLE_GRADLE_VERSIONS_SPOCK_1.empty },
    reason = "Gradle 9 requires at least Java 17, but Spock 1 isn't compatible with this version anymore"
)
@IgnoreIf(
    value = { effectiveTestJavaMajorVersion() >= 17 },
    reason = "Spock 1 tests do not run with Java 17 or higher"
)
class SpockViaJUnitVintageFuncTest extends SpockBaseJunit5FuncTest {

    @Override
    boolean isRerunsParameterizedMethods() {
        false
    }

    @Override
    protected String beforeClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    @Override
    protected String afterClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "executionError"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation "org.codehaus.groovy:groovy:2.5.8"
                testImplementation "${spock1Dependency()}"
                testImplementation "${jupiterApiDependency()}"
                testRuntimeOnly "${junitVintageEngineDependency()}"
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle.
                testRuntimeOnly "${junitPlatformLauncherDependency()}"
            }

            test {
                useJUnitPlatform()
            }
        """.stripIndent()
    }
}
