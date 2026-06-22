/*
 * Copyright 2016 the original author or authors.
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

import spock.lang.Issue

class SpringDependencyManagementPluginSmokeTest extends AbstractPluginValidatingSmokeTest {
    @Issue('https://plugins.gradle.org/plugin/io.spring.dependency-management')
    def 'spring dependency management plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.spring.dependency-management' version '${TestedVersions.springDependencyManagement}' // TODO:Finalize Upload Removal - Issue #21439
            }

            ${mavenCentralRepository()}

            dependencyManagement {
                dependencies {
                    dependency 'org.springframework:spring-core:4.0.3.RELEASE'
                    dependency("commons-logging:commons-logging:1.1.2")
                }
            }

            dependencies {
                implementation 'org.springframework:spring-core'
            }
            """.stripIndent()

        when:
        def result = runner("dependencies", "--configuration", "compileClasspath")
            .expectDeprecationWarning(
                "The ResolutionStrategy.eachDependency(Action) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the dependencySubstitution(Action) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_resolution_deprecations",
                "https://github.com/spring-gradle-plugins/dependency-management-plugin/blob/e2237bdfa2b68dd365e5a0887cf80c1815bb313d/src/main/java/io/spring/gradle/dependencymanagement/internal/VersionConfiguringAction.java#L104"
            )
            .expectDeprecationWarning(
                "The LenientConfiguration.getArtifacts() method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the ArtifactView#getArtifacts() method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_resolution_deprecations",
                "https://github.com/spring-gradle-plugins/dependency-management-plugin/blob/e2237bdfa2b68dd365e5a0887cf80c1815bb313d/src/main/java/io/spring/gradle/dependencymanagement/internal/maven/MavenPomResolver.java#L79"
            )
            .expectDeprecationWarning(
                "The ResolvedConfiguration.getResolvedArtifacts() method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the ResolvableDependencies#getArtifacts() method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_resolution_deprecations",
                "https://github.com/spring-gradle-plugins/dependency-management-plugin/blob/e2237bdfa2b68dd365e5a0887cf80c1815bb313d/src/main/java/io/spring/gradle/dependencymanagement/internal/maven/ConfigurationModelResolver.java#L91"
            )
            .build()

        then:
        result.output.contains('org.springframework:spring-core -> 4.0.3.RELEASE')
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'io.spring.dependency-management' : Versions.of(TestedVersions.springDependencyManagement)
        ]
    }

    @Override
    String getSubprojectExtensionAccess(String testedPluginId, String version) {
        "dependencyManagement {}"
    }

    @Override
    List<String> getSubprojectExtensionDeprecations(String testedPluginId, String version) {
        [parentMethodInvocationDeprecation('dependencyManagement')]
    }
}
