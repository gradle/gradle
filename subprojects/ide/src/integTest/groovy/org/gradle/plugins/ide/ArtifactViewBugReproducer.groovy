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

package org.gradle.plugins.ide

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactViewBugReproducer extends AbstractIntegrationSpec {
    /**
     * Reproduce a bug where when an artifactView over a configuration
     * that has a dependency on a component with a classifier does not
     * resolve the artifacts for the reselected variant, but continues
     * to resolve the classifier artifact.
     */
    def "test"() {
        given:
        def module = mavenRepo.module("org", "foo", "1.0")
        module.artifact(classifier: "cls")
        module.artifact(classifier: "sources")
        module.artifact(classifier: "javadoc")
        module.publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation("org:foo:1.0:cls")
            }

            repositories {
                maven {
                    url = uri("${mavenRepo.uri}")
                }
            }

            task resolve {
                def result = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                    }
                }.artifacts.fileCollection
                doLast {
                    assert result.files*.name == ["foo-1.0-sources.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
