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

package org.gradle.integtests.resolve.derived

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.MavenHttpModule

class PomVariantResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    MavenHttpModule direct
    MavenHttpModule transitive

    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                maven { url = '$mavenHttpRepo.uri' }
            }

            dependencies {
                implementation 'test:direct:1.0'
            }

            abstract class Resolve extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getArtifacts()

                @Internal
                List<String> expectedFiles = []

                @TaskAction
                void assertThat() {
                    assert artifacts.files*.name.sort() == expectedFiles.sort()
                }
            }

            task resolvePom(type: Resolve) {
                def artifactView = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.METADATA))
                        attribute(MetadataFormat.METADATA_FORMAT_ATTRIBUTE, objects.named(MetadataFormat, MetadataFormat.MAVEN))
                    }
                }
                artifacts.from(artifactView.files)
            }

            task resolveRuntime(type: Resolve) {
                artifacts.from(configurations.runtimeClasspath.incoming.files)
            }
        """
        transitive = mavenHttpRepo.module("test", "transitive", "1.0")
        direct = mavenHttpRepo.module("test", "direct", "1.0")
        direct.dependsOn(transitive)
    }

    def "can resolve POM files for direct and transitive dependencies"() {
        transitive.publish()
        direct.publish()

        buildFile << """
            tasks.resolvePom {
                expectedFiles = ['direct-1.0.pom', 'transitive-1.0.pom']
            }
        """
        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()

        succeeds('resolvePom')
    }

    def "POM variant is not selected during normal dependency resolution"() {
        transitive.publish()
        direct.publish()

        buildFile << """
            tasks.resolveRuntime {
                expectedFiles = ['direct-1.0.jar', 'transitive-1.0.jar']
            }
        """
        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        direct.artifact.expectGet()
        transitive.artifact.expectGet()

        succeeds('resolveRuntime')
    }

    def "POM file content is valid XML"() {
        transitive.publish()
        direct.publish()

        buildFile << """
            tasks.resolvePom {
                expectedFiles = ['direct-1.0.pom', 'transitive-1.0.pom']
            }
        """
        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()

        succeeds('resolvePom')
    }
}
