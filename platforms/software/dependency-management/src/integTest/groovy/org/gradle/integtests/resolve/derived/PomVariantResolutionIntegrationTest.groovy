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

/**
 * Tests for POM variant resolution via ArtifactView with variant reselection.
 */
class PomVariantResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "can resolve POM files for direct and transitive dependencies"() {
        def transitive = mavenHttpRepo.module("test", "transitive", "1.0").publish()
        def direct = mavenHttpRepo.module("test", "direct", "1.0").dependsOn(transitive).publish()

        buildFile.text = pomResolveBuildFile()

        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        succeeds('resolvePom')
        outputContains('direct-1.0.pom')
        outputContains('transitive-1.0.pom')
    }

    def "POM variant includes parent POMs"() {
        def parent = mavenHttpRepo.module("test", "parent-a", "1.0").hasPackaging("pom").publish()
        def transitive = mavenHttpRepo.module("test", "transitive-a", "1.0").publish()
        def direct = mavenHttpRepo.module("test", "direct-a", "1.0").dependsOn(transitive).parent("test", "parent-a", "1.0").publish()

        buildFile.text = pomResolveBuildFile('test:direct-a:1.0')

        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        parent.pom.allowGetOrHead()
        succeeds('resolvePom')
        outputContains('direct-a-1.0.pom')
        outputContains('parent-a-1.0.pom')
        outputContains('transitive-a-1.0.pom')
    }

    def "POM variant includes grandparent POMs"() {
        def grandparent = mavenHttpRepo.module("test", "grandparent-b", "1.0").hasPackaging("pom").publish()
        def parent = mavenHttpRepo.module("test", "parent-b", "1.0").hasPackaging("pom").parent("test", "grandparent-b", "1.0").publish()
        def transitive = mavenHttpRepo.module("test", "transitive-b", "1.0").publish()
        def direct = mavenHttpRepo.module("test", "direct-b", "1.0").dependsOn(transitive).parent("test", "parent-b", "1.0").publish()

        buildFile.text = pomResolveBuildFile('test:direct-b:1.0')

        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        parent.pom.allowGetOrHead()
        grandparent.pom.allowGetOrHead()
        succeeds('resolvePom')
        outputContains('direct-b-1.0.pom')
        outputContains('grandparent-b-1.0.pom')
        outputContains('parent-b-1.0.pom')
        outputContains('transitive-b-1.0.pom')
    }

    def "POM variant includes both versions when two modules share a parent at different versions"() {
        def parentV1 = mavenHttpRepo.module("test", "parent-c", "1.0").hasPackaging("pom").publish()
        def parentV2 = mavenHttpRepo.module("test", "parent-c", "2.0").hasPackaging("pom").publish()
        def transitive = mavenHttpRepo.module("test", "transitive-c", "1.0").parent("test", "parent-c", "2.0").publish()
        def direct = mavenHttpRepo.module("test", "direct-c", "1.0").dependsOn(transitive).parent("test", "parent-c", "1.0").publish()

        buildFile.text = pomResolveBuildFile('test:direct-c:1.0')

        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        parentV1.pom.allowGetOrHead()
        parentV2.pom.allowGetOrHead()
        succeeds('resolvePom')
        outputContains('direct-c-1.0.pom')
        outputContains('parent-c-1.0.pom')
        outputContains('parent-c-2.0.pom')
        outputContains('transitive-c-1.0.pom')
    }

    def "POM variant is synthesized when Gradle Module Metadata is present but has no pom variant"() {
        def transitive = mavenHttpRepo.module("test", "transitive-d", "1.0").withModuleMetadata().publish()
        def direct = mavenHttpRepo.module("test", "direct-d", "1.0").dependsOn(transitive).withModuleMetadata().publish()

        buildFile.text = pomResolveBuildFile('test:direct-d:1.0')

        expect:
        direct.pom.allowGetOrHead()
        direct.moduleMetadata.expectGet()
        transitive.pom.allowGetOrHead()
        transitive.moduleMetadata.expectGet()
        succeeds('resolvePom')
        outputContains('direct-d-1.0.pom')
        outputContains('transitive-d-1.0.pom')
    }

    def "POM variant is not selected during normal dependency resolution"() {
        def transitive = mavenHttpRepo.module("test", "transitive-e", "1.0").publish()
        def direct = mavenHttpRepo.module("test", "direct-e", "1.0").dependsOn(transitive).publish()

        buildFile.text = """
            plugins { id 'java' }
            repositories { maven { url = '$mavenHttpRepo.uri' } }
            dependencies { implementation 'test:direct-e:1.0' }
            task resolveRuntime {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    def fileNames = files*.name
                    assert !fileNames.any { it.endsWith('.pom') }
                }
            }
        """

        expect:
        direct.pom.allowGetOrHead()
        transitive.pom.allowGetOrHead()
        direct.artifact.expectGet()
        transitive.artifact.expectGet()
        succeeds('resolveRuntime')
    }

    private String pomResolveBuildFile(String dependency = 'test:direct:1.0') {
        return """
            plugins { id 'java' }
            repositories { maven { url = '$mavenHttpRepo.uri' } }
            dependencies { implementation '$dependency' }
            task resolvePom {
                def view = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.METADATA))
                        attribute(MetadataFormat.METADATA_FORMAT_ATTRIBUTE, objects.named(MetadataFormat, MetadataFormat.MAVEN))
                    }
                }
                def files = view.files
                doLast {
                    files.each { println it.name }
                }
            }
        """
    }
}
