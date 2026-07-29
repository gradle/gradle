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

// Spock 1 requires Groovy 2.5; not resolvable against current Gradle's Groovy 4 runtime.
// Java 17+ (required by Gradle 9) is also incompatible with Spock 1.
@spock.lang.Ignore
class SpockViaJUnitVintageFuncTest extends SpockBaseJunit5FuncTest {

    @Override
    boolean isRerunsParameterizedMethods() {
        false
    }

    @Override
    protected String beforeClassErrorTestMethodName() {
        "initializationError"
    }

    @Override
    protected String afterClassErrorTestMethodName() {
        "executionError"
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
