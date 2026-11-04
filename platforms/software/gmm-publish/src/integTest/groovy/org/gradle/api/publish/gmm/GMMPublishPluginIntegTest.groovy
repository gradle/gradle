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

package org.gradle.api.publish.gmm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.ivy.IvyJavaModule
import org.gradle.test.fixtures.maven.MavenJavaModule

class GMMPublishPluginIntegTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'publishTest'"
    }

    private static String customVariant() {
        """
            group = 'org.gradle.test'
            version = '1.9'

            def customDeps = configurations.dependencyScope('customDeps')
            def custom = configurations.consumable('custom') {
                extendsFrom(customDeps.get())
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'custom-usage'))
                }
            }
            dependencies {
                customDeps 'log4j:log4j:1.2.17'
            }
        """
    }

    def "maven publish plugin can publish a minimal custom variant"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }
            ${customVariant()}
            components.java.addVariantsFromConfiguration(custom.get()) {
                mapToMavenScope('runtime')
            }
            publishing {
                publications {
                    myPub(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
        """

        when:
        succeeds "publishMyPubPublicationToMavenRepository"

        then:
        def module = new MavenJavaModule(mavenRepo.module("org.gradle.test", "publishTest", "1.9"), [MavenJavaModule.MAIN_FEATURE], false)
        module.assertPublished(['apiElements', 'custom', 'runtimeElements'] as Set)
        with(module.parsedModuleMetadata) {
            variant("apiElements") { noMoreDependencies() }
            variant("runtimeElements") { noMoreDependencies() }
            variant("custom") {
                dependency('log4j:log4j:1.2.17').exists()
                noMoreDependencies()
            }
        }
        module.parsedPom.scopes.size() == 1
        module.parsedPom.scopes.runtime.assertDependsOn("log4j:log4j:1.2.17")
    }

    def "ivy publish plugin can publish a minimal custom variant"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
                id 'ivy-publish'
            }
            ${customVariant()}
            components.java.addVariantsFromConfiguration(custom.get()) {}
            publishing {
                publications {
                    myPub(IvyPublication) {
                        from components.java
                    }
                }
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
            }
        """

        when:
        succeeds "publishMyPubPublicationToIvyRepository"

        then:
        def module = new IvyJavaModule(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))
        module.assertPublished(['apiElements', 'custom', 'runtimeElements'] as Set)
        with(module.parsedModuleMetadata) {
            variant("apiElements") { noMoreDependencies() }
            variant("runtimeElements") { noMoreDependencies() }
            variant("custom") {
                dependency('log4j:log4j:1.2.17').exists()
                noMoreDependencies()
            }
        }
        with(module.parsedIvy) {
            configurations.keySet() == ["default", "compile", "custom", "runtime"] as Set
            configurations["default"].extend == ["runtime", "custom"] as Set
            configurations["runtime"].extend == null
            expectArtifact("publishTest").hasAttributes("jar", "jar", ["compile", "runtime"])
        }
        module.assertApiDependencies()
        module.assertRuntimeDependencies()
    }

    def "gmm publish plugin can publish a minimal custom variant to a flat directory repository"() {
        given:
        def repoDir = file("myFileRepo")
        buildFile << """
            plugins {
                id 'java-library'
                id 'gmm-publish'
            }
            ${customVariant()}
            components.java.addVariantsFromConfiguration(custom.get()) {}
            publishing {
                repositories {
                    flatDir {
                        name = 'myFileRepo'
                        dirs 'myFileRepo'
                    }
                }
                publications {
                    gmm(GMMPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publishGmmPublicationToMyFileRepoRepository'

        then:
        executed(':generateMetadataFileForGmmPublication')
        repoDir.file("module.json").exists()
        with(new GradleModuleMetadata(repoDir.file("module.json"))) {
            variant("apiElements") { noMoreDependencies() }
            variant("runtimeElements") { noMoreDependencies() }
            variant("custom") {
                dependency('log4j:log4j:1.2.17').exists()
                noMoreDependencies()
            }
        }
    }

    def "publish lifecycle task publishes gmm publications"() {
        given:
        def repoDir = file("myFileRepo")
        buildFile << """
            plugins {
                id 'java-library'
                id 'gmm-publish'
            }
            group = 'org.gradle.test'
            version = '1.9'
            publishing {
                repositories {
                    flatDir {
                        name = 'myFileRepo'
                        dirs 'myFileRepo'
                    }
                }
                publications {
                    gmm(GMMPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        executed(':publishGmmPublicationToMyFileRepoRepository')
        repoDir.file("module.json").exists()
    }

    def "can apply only the gmm-publish plugin"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
                id 'gmm-publish'
            }
            group = 'org.gradle.test'
            version = '1.9'
        """

        when:
        succeeds 'tasks'

        then:
        outputContains "publish - Publishes all publications produced by this project."

        expect:
        succeeds "publish"
    }
}
