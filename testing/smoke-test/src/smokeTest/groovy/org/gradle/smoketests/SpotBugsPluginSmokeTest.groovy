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

class SpotBugsPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue('https://plugins.gradle.org/plugin/com.github.spotbugs')
    def 'spotbugs plugin'() {
        given:
        buildFile << """
            import com.github.spotbugs.snom.SpotBugsTask

            plugins {
                id 'java'
                id 'com.github.spotbugs' version '${TestedVersions.spotbugs}'
            }

            ${mavenCentralRepository()}

            tasks.withType(SpotBugsTask) {
                reports.create("html")
            }

            """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()

        when:
        runner('spotbugsMain')
            .expectDeprecationWarning(
                "The ResolutionStrategy.eachDependency(Action) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the dependencySubstitution(Action) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_resolution_deprecations",
                "https://github.com/spotbugs/spotbugs-gradle-plugin/blob/18ee52e0b199126d2499e11fdac6a41b3dc5ce59/src/main/kotlin/com/github/spotbugs/snom/SpotBugsBasePlugin.kt#L78"
            )
            .build()

        then:
        file('build/reports/spotbugs').isDirectory()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.github.spotbugs': Versions.of(TestedVersions.spotbugs)
        ]
    }

    @Override
    String getSubprojectExtensionAccess(String testedPluginId, String version) {
        "spotbugs {}"
    }

    @Override
    List<String> getSubprojectExtensionDeprecations(String testedPluginId, String version) {
        [parentMethodInvocationDeprecation('spotbugs')]
    }
}
