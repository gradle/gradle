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

class JUnit4ViaJUnitVintageFuncTest extends JUnit4FuncTest {

    @Override
    protected isRerunsAllParameterizedIterations() {
        true
    }

    @Override
    protected String initializationErrorSyntheticTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    @Override
    protected String afterClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "executionError"
    }

    @Override
    protected String beforeClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation "${junit4Dependency()}"
                testImplementation "${jupiterApiDependency()}"
                testRuntimeOnly "${junitVintageEngineDependency()}"
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle. 
                testRuntimeOnly "${junitPlatformLauncherDependency()}"
            }

            test {
                useJUnitPlatform()
            }
        """
    }
}
