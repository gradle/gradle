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

package org.gradle.integtests.resolve.api

import org.gradle.api.attributes.Usage
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class AdhocArtifactDeduplicationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                }
            }
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            tasks.register("printArtifacts") {
                def artifacts = configurations.res.incoming.artifacts.resolvedArtifacts
                doLast {
                    println("artifacts: " + artifacts.get().collect { "\${it.id.displayName} -> \${it.file.name}" }.sort())
                }
            }
        """
    }

    def "ad hoc artifact is deduplicated against the same file from the module (gmm: #gmm)"() {
        publishLib(gmm)

        buildFile << """
            dependencies {
                deps("org:lib:1.0")
                deps("org:lib:1.0@jar")
            }
        """

        when:
        succeeds("printArtifacts")

        then:
        outputContains("artifacts: [lib-1.0.jar (org:lib:1.0) -> lib-1.0.jar]")

        where:
        gmm << [true, false]
    }

    def "ad hoc artifact is not deduplicated against a different file from the module (gmm: #gmm)"() {
        publishLib(gmm)

        buildFile << """
            dependencies {
                deps("org:lib:1.0")
                deps("org:lib:1.0@pom")
            }
        """

        when:
        succeeds("printArtifacts")

        then:
        outputContains("artifacts: [lib-1.0.jar (org:lib:1.0) -> lib-1.0.jar, lib-1.0.pom (org:lib:1.0) -> lib-1.0.pom]")

        where:
        gmm << [true, false]
    }

    @Issue("https://github.com/gradle/gradle/issues/38831")
    def "ad hoc artifact is not deduplicated against a Gradle Module Metadata file with a different destination file name"() {
        mavenRepo.module("org", "lib", "1.0")
            .adhocVariants()
            .variant("runtimeElements", ["org.gradle.usage": Usage.JAVA_RUNTIME]) {
                artifact("liblib.jar", "lib-1.0.jar")
            }
            .withModuleMetadata()
            .publish()

        buildFile << """
            dependencies {
                deps("org:lib:1.0")
                deps("org:lib:1.0@jar")
            }
        """

        when:
        succeeds("printArtifacts")

        then:
        outputContains("artifacts: [lib-1.0.jar (org:lib:1.0) -> lib-1.0.jar, liblib.jar (org:lib:1.0) -> liblib.jar]")
    }

    private void publishLib(boolean gmm) {
        def module = mavenRepo.module("org", "lib", "1.0")
        if (gmm) {
            module.withModuleMetadata()
        }
        module.publish()
    }
}
