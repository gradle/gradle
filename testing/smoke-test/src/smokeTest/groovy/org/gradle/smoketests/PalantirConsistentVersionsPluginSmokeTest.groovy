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
        runner('--write-locks').build()

        then:
        file("versions.lock").exists()

        when:
        def result = runner("other:dependencies").build()

        then:
        result.output.contains("com.google.guava:guava -> 17.0")
    }

    @Override
    SmokeTestGradleRunner runner(String... tasks) {
        return super.runner(tasks)
            .expectDeprecationWarning(
                "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_project_notation",
                "https://github.com/palantir/gradle-consistent-versions/issues/1637"
            )
    }
}
