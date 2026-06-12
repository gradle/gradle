/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.util.GradleVersion

class SigstoreSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'dev.sigstore.sign': TestedVersions.sigstore
        ]
    }

    def "can configure sigstore for java maven publication"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
                id("maven-publish")
                id("dev.sigstore.sign").version("${TestedVersions.sigstore}")
            }

            ${mavenCentralRepository()}

            publishing {
                repositories {
                    maven {
                        url = uri(layout.buildDirectory.dir('repo'))
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        // Actually running sigstore's signing tasks requires authentication,
        // so just make sure configuration works correctly
        def result = runner('tasks', '--all')
            .expectLegacyDeprecationWarning("The 'val name by creating { }' property delegate syntax has been deprecated. This is scheduled to be removed in Gradle 10. Use 'val element = create(name) { }' instead. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_delegated_properties")
            .expectLegacyDeprecationWarning("The 'val name by creating { }' property delegate syntax has been deprecated. This is scheduled to be removed in Gradle 10. Use 'val element = create(name) { }' instead. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_delegated_properties")
            .run()

        then:
        result.output.contains("sigstoreSignMavenPublication - Sign all artifacts in maven publication in Sigstore")
    }

}
