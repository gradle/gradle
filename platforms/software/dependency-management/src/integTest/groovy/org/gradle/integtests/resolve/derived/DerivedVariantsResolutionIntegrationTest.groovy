/*
 * Copyright 2021 the original author or authors.
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

class DerivedVariantsResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
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

            task resolveJavadoc(type: Resolve) {
                def artifactView = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JAVADOC))
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

    // region With Gradle Module Metadata
    def "direct has GMM and no sources or javadoc jars"() {
        transitive.withModuleMetadata()
        transitive.publish()
        direct.withModuleMetadata()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = []
            }
            resolveJavadoc {
                expectedFiles = []
            }
        """
        expect:
        direct.pom.expectGet()
        direct.moduleMetadata.expectGet()
        transitive.pom.expectGet()
        transitive.moduleMetadata.expectGet()

        succeeds( 'resolveSources', 'resolveJavadoc')
    }

    def "direct has GMM and has sources jar"() {
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

    def "direct has GMM and has javadoc jar"() {
        transitive.adhocVariants().variant("jar", [
            "org.gradle.category": "library",
            "org.gradle.dependency.bundling": "external",
            "org.gradle.usage": "java-runtime"
        ]) {
            artifact("transitive-1.0.jar")
        }
            .variant("javadoc", [
                "org.gradle.category": "documentation",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.docstype": "javadoc",
                "org.gradle.usage": "java-runtime"
            ]) {
                artifact("transitive-1.0-javadoc.jar")
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
            .variant("javadoc", [
                "org.gradle.category": "documentation",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.docstype": "javadoc",
                "org.gradle.usage": "java-runtime"
            ]) {
                artifact("direct-1.0-javadoc.jar")
            }
        direct.withModuleMetadata()
        direct.publish()

        buildFile << """
            resolveJavadoc {
                expectedFiles = ['direct-1.0-javadoc.jar', 'transitive-1.0-javadoc.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        direct.moduleMetadata.expectGet()
        transitive.pom.expectGet()
        transitive.moduleMetadata.expectGet()
        direct.artifact(classifier: "javadoc").expectGet()
        transitive.artifact(classifier: "javadoc").expectGet()

        succeeds( "resolveJavadoc")
    }

    def "direct has GMM and has both sources and javadoc jars"() {
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
            .variant("javadoc", [
                "org.gradle.category": "documentation",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.docstype": "javadoc",
                "org.gradle.usage": "java-runtime"
            ]) {
                artifact("transitive-1.0-javadoc.jar")
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
            .variant("javadoc", [
                "org.gradle.category": "documentation",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.docstype": "javadoc",
                "org.gradle.usage": "java-runtime"
            ]) {
                artifact("direct-1.0-javadoc.jar")
            }
        direct.withModuleMetadata()
        direct.publish()

        buildFile << """
            resolveJavadoc {
                expectedFiles = ['direct-1.0-javadoc.jar', 'transitive-1.0-javadoc.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        direct.moduleMetadata.expectGet()
        transitive.pom.expectGet()
        transitive.moduleMetadata.expectGet()
        direct.artifact(classifier: 'javadoc').expectGet()
        transitive.artifact(classifier: 'javadoc').expectGet()

        succeeds( 'resolveJavadoc')

        and:
        buildFile << """
            resolveSources {
                expectedFiles = ['direct-1.0-sources.jar', 'transitive-1.0-sources.jar']
            }
        """

        // POMs and GMM are already cached; querying for sources should do minimal additional work to fetch sources jars
        direct.artifact(classifier: 'sources').expectGet()
        transitive.artifact(classifier: 'sources').expectGet()

        succeeds( 'resolveSources')
    }

    def "direct has GMM and no sources jar and transitive has GMM and has sources jar"() {
        transitive.adhocVariants().variant("jar", [
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.usage": "java-runtime"
        ]) {
            artifact("transitive-1.0.jar")
        }.variant("sources", [
            "org.gradle.category": "documentation",
            "org.gradle.dependency.bundling": "external",
            "org.gradle.docstype": "sources",
            "org.gradle.usage": "java-runtime"
        ]) {
            artifact("transitive-1.0-sources.jar")
        }
        transitive.withModuleMetadata()
        transitive.publish()

        direct.withModuleMetadata()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = ['transitive-1.0-sources.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        direct.moduleMetadata.expectGet()
        transitive.pom.expectGet()
        transitive.moduleMetadata.expectGet()
        transitive.artifact(classifier: "sources").expectGet()

        succeeds( "resolveSources")
    }

    def "direct has no GMM and no sources or javadoc jars"() {
        transitive.publish()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = []
            }
            resolveJavadoc {
                expectedFiles = []
            }
        """
        expect:
        direct.pom.expectGet()
        transitive.pom.expectGet()
        direct.artifact(classifier: "sources").expectHeadMissing()
        transitive.artifact(classifier: "sources").expectHeadMissing()
        direct.artifact(classifier: "javadoc").expectHeadMissing()
        transitive.artifact(classifier: "javadoc").expectHeadMissing()

        succeeds( 'resolveSources', 'resolveJavadoc')
    }

    def "direct has no GMM and has sources jar"() {
        direct.withSourceAndJavadoc()
        transitive.withSourceAndJavadoc()

        transitive.publish()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = ['direct-1.0-sources.jar', 'transitive-1.0-sources.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        transitive.pom.expectGet()
        direct.artifact(classifier: "sources").expectHead()
        transitive.artifact(classifier: "sources").expectHead()
        direct.artifact(classifier: "sources").expectGet()
        transitive.artifact(classifier: "sources").expectGet()

        succeeds("resolveSources")
    }

    def "direct has no GMM and has javadoc jar"() {
        direct.withSourceAndJavadoc()
        transitive.withSourceAndJavadoc()

        transitive.publish()
        direct.publish()

        buildFile << """
            resolveJavadoc {
                expectedFiles = ['direct-1.0-javadoc.jar', 'transitive-1.0-javadoc.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        transitive.pom.expectGet()
        direct.artifact(classifier: "javadoc").expectHead()
        transitive.artifact(classifier: "javadoc").expectHead()
        direct.artifact(classifier: "javadoc").expectGet()
        transitive.artifact(classifier: "javadoc").expectGet()

        succeeds("resolveJavadoc")
    }

    def "direct has no GMM and has both sources and javadoc jars"() {
        direct.withSourceAndJavadoc()
        transitive.withSourceAndJavadoc()

        transitive.publish()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = ['direct-1.0-sources.jar', 'transitive-1.0-sources.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        transitive.pom.expectGet()
        direct.artifact(classifier: "sources").expectHead()
        transitive.artifact(classifier: "sources").expectHead()
        direct.artifact(classifier: "sources").expectGet()
        transitive.artifact(classifier: "sources").expectGet()

        succeeds("resolveSources")

        and:
        buildFile << """
            resolveJavadoc {
                expectedFiles = ['direct-1.0-javadoc.jar', 'transitive-1.0-javadoc.jar']
            }
        """

        // POMs and GMM are already cached; querying for javadoc should do minimal additional work to fetch javadoc jars
        direct.artifact(classifier: "javadoc").expectHead()
        transitive.artifact(classifier: "javadoc").expectHead()
        direct.artifact(classifier: 'javadoc').expectGet()
        transitive.artifact(classifier: 'javadoc').expectGet()

        succeeds( 'resolveJavadoc')
    }

    def "direct has no GMM and no sources jar and transitive has no GMM and has sources jar"() {
        transitive.withSourceAndJavadoc()
        transitive.publish()
        direct.publish()

        buildFile << """
            resolveSources {
                expectedFiles = ['transitive-1.0-sources.jar']
            }
        """
        expect:
        direct.pom.expectGet()
        transitive.pom.expectGet()
        direct.artifact(classifier: "sources").expectHeadMissing()
        transitive.artifact(classifier: "sources").expectHead()
        transitive.artifact(classifier: "sources").expectGet()

        succeeds( "resolveSources")
    }
    // endregion
}
