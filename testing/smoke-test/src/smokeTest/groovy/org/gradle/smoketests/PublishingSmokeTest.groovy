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

package org.gradle.smoketests

class PublishingSmokeTest extends AbstractPluginValidatingSmokeTest {
    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.vanniktech.maven.publish': TestedVersions.vanniktechMavenPublish,
        ]
    }

    def "can publish with vanniktech maven publish plugin"() {
        given:
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
                id 'com.vanniktech.maven.publish' version '${TestedVersions.vanniktechMavenPublish}'
            }

            group = 'org.example'
            version = '1.0.0'

            ${mavenCentralRepository()}
            publishing {
                repositories {
                    maven {
                        name = "test"
                        url = uri("\$buildDir/repo")
                    }
                }
            }

            dependencies {
                api 'com.google.guava:guava:31.1-jre'
            }
        """
        file("src/main/java/org/example/Library.java") << """
            package org.example;

            import com.google.common.collect.ImmutableList;

            public class Library {
                public static ImmutableList<String> getNames() {
                    return ImmutableList.of("Alice", "Bob", "Charlie");
                }
            }
        """
        file("src/testFixtures/java/org/example/LibraryTestFixtures.java") << """
            package org.example;

            import com.google.common.collect.ImmutableList;

            public class LibraryTestFixtures {
                public static ImmutableList<String> getTestNames() {
                    return ImmutableList.of("Test Alice", "Test Bob", "Test Charlie");
                }
            }
        """

        when:
        runner('publishAllPublicationsToTestRepository').build()

        then:
        file('build/repo/org/example/test/1.0.0/test-1.0.0.pom').exists()
        file('build/repo/org/example/test/1.0.0/test-1.0.0.jar').exists()
        file('build/repo/org/example/test/1.0.0/test-1.0.0-test-fixtures.jar').exists()
    }
}
