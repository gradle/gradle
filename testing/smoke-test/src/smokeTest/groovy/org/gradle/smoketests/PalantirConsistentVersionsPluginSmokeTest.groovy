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

package org.gradle.smoketests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.Jdk11OrLater)
class PalantirConsistentVersionsPluginSmokeTest extends AbstractSmokeTest {

    def 'basic functionality'() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("com.palantir.consistent-versions") version "${TestedVersions.palantirConsistentVersions}"
            }
            ${mavenCentralRepository()}
        """

        file("settings.gradle") << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id("java")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("com.google.guava:guava")
            }
        """
        file("versions.props") << "com.google.guava:guava = 17.0"

        when:
        def result = runner('--write-locks')
            // They are doing some weird stuff in an afterEvaluate
            // See: https://github.com/palantir/gradle-consistent-versions/blob/28a604723c936f5c93c6591e144c4a1731d570ad/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L277
            .buildAndFail()

        then:
        // This method has been removed in Gradle 9.0
        result.output.contains("ProjectDependency.getDependencyProject")
    }
}
