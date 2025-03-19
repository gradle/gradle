/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/22144")
class ForceRealizedMetadataIntegrationTest extends AbstractHttpDependencyResolutionTest {
    MavenHttpModule direct
    MavenHttpModule transitive

    def setup() {
        executer.withArgument("-Dorg.gradle.integtest.force.realize.metadata=true")

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

                @InputFiles
                abstract ConfigurableFileCollection getArtifactCollection()

                @Internal
                List<String> expectedFiles = []

                @TaskAction
                void assertThat() {
                    assert artifacts.files*.name == expectedFiles
                    assert artifactCollection.files*.name == expectedFiles
                }
            }

            task resolveSources(type: Resolve) {
                def artifactView = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                }
                artifacts.from(artifactView.files)
                artifactCollection.from(artifactView.artifacts.artifactFiles)
            }
        """
        transitive = mavenHttpRepo.module("test", "transitive", "1.0")
        direct = mavenHttpRepo.module("test", "direct", "1.0")
        direct.dependsOn(transitive)
    }

    def "can resolve derived variants"() {
        transitive.adhocVariants().variant("jar", [
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.usage": "java-runtime"
        ]) {
            artifact("transitive-1.0.jar")
        }
                .variant("sources", [
                        "org.gradle.category": "documentation",
                        "org.gradle.dependency.bundling": "external",
                        "org.gradle.docstype": "sources",
                        "org.gradle.usage": "java-runtime"
                ]) {
                    artifact("transitive-1.0-sources.jar")
                }
        transitive.withModuleMetadata()
        transitive.publish()

        direct.adhocVariants().variant("jar", [
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.usage": "java-runtime"
        ]) {
            artifact("direct-1.0.jar")
        }
                .variant("sources", [
                        "org.gradle.category": "documentation",
                        "org.gradle.dependency.bundling": "external",
                        "org.gradle.docstype": "sources",
                        "org.gradle.usage": "java-runtime"
                ]) {
                    artifact("direct-1.0-sources.jar")
                }
        direct.withModuleMetadata()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = ['direct-1.0-sources.jar', 'transitive-1.0-sources.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        direct.moduleMetadata.expectGet()
        transitive.pom.expectGet()
        transitive.moduleMetadata.expectGet()
        direct.artifact(classifier: "sources").expectGet()
        transitive.artifact(classifier: "sources").expectGet()

        succeeds( "resolveSources")
    }
}
