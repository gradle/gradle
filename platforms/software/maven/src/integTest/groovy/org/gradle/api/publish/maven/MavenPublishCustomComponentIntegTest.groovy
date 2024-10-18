/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenPublishCustomComponentIntegTest extends AbstractMavenPublishIntegTest {
    def publishedModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    def "cannot publish custom component with no usages or variants"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from new MySoftwareComponent()
                    }
                }
            }
""")

        when:
        fails "publish"

        then:
        failure.assertHasCause """Invalid publication 'maven':
  - This publication must publish at least one variant"""
    }

    def "can publish custom component with usages"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from new MyComponentWithUsages()
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        publishedModule.assertPublished()
        publishedModule.parsedPom.scopes.runtime.assertDependsOn('group:module:1.0')
        publishedModule.parsedModuleMetadata.variants*.name == ["usage"]
        publishedModule.parsedModuleMetadata.variant("usage").files*.name == ["publishTest-1.9.text"]
        publishedModule.parsedModuleMetadata.variant("usage").dependencies*.coords == ['group:module:1.0']
    }

    def "can publish custom component with variants (with proper unique SNAPSHOT handling)"() {
        createBuildScripts("""
            publishing {
                publications {
                    nestedVariant(MavenPublication) {
                        from MyComponentWithVariants.nestedVariant
                        artifactId = 'nested'
                    }
                    maven(MavenPublication) {
                        from new MyComponentWithVariants()
                    }
                }
            }

            version = "1.9-SNAPSHOT"
""")

        publishedModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9-SNAPSHOT")

        when:
        run "publish"

        then:
        publishedModule.assertPublished()
        publishedModule.parsedPom.scopes.isEmpty()
        publishedModule.parsedModuleMetadata.variants*.name == ["usage"]
        with(publishedModule.parsedModuleMetadata.variant("usage")) { variant ->
            variant.files.empty
            variant.dependencies.empty
            variant.availableAt.coords == 'org.gradle.test:nested:1.9-SNAPSHOT'
            variant.availableAt.url == "../../nested/1.9-SNAPSHOT/nested-1.9-SNAPSHOT.module"
        }

        when:
        mavenRepo.module('group', 'module', '1.0').publish()

        def otherSettings = file('consumer/settings.gradle')
        def otherBuild = file('consumer/build.gradle')

        otherSettings << "rootProject.name = 'consumer'"
        otherBuild << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }

            dependencies {
                conf('org.gradle.test:publishTest:1.9-SNAPSHOT') {
                    attributes {
                        attribute(Attribute.of("test.attribute", String), "value")
                    }
                }
            }

            task resolve {
                def conf = configurations.conf
                doLast {
                    println conf.files
                }
            }
"""
        executer.inDirectory(file('consumer'))

        and:
        succeeds 'resolve'

        then:
        outputContains('nested-1.9-SNAPSHOT.text')
        outputContains('module-1.0.jar')
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            def publishedFile = file('publishTest-1.9.text') << 'content'

            configurations { foo }
            MyComponentWithUsages.publishedArtifact = artifacts.add("foo", publishedFile)
            MyComponentWithUsages.publishedDependency = dependencies.add("foo", "group:module:1.0")
            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            class TestAttributes {
                // shared mutable state for tests, don't do this at home!
                static AttributeContainer INSTANCE
            }
            TestAttributes.INSTANCE = project.services.get(org.gradle.api.internal.attributes.AttributesFactory)
               .mutable()
               .attribute(Attribute.of("test.attribute", String), "value")

            class MySoftwareComponent implements org.gradle.api.internal.component.SoftwareComponentInternal {
                String name = 'comp'
                Set<org.gradle.api.internal.component.UsageContext> getUsages() {
                    return Collections.emptySet()
                }
            }
            class MyComponentWithUsages extends MySoftwareComponent {
                static PublishArtifact publishedArtifact
                static ModuleDependency publishedDependency

                Set<org.gradle.api.internal.component.UsageContext> getUsages() {
                    return [ new MyUsageContext() ]
                }

                class MyUsageContext implements org.gradle.api.internal.component.UsageContext {
                    String name = "usage"
                    Usage usage = { "usageName" }
                    AttributeContainer attributes = TestAttributes.INSTANCE
                    Set<PublishArtifact> artifacts = [ publishedArtifact ]
                    Set<ModuleDependency> dependencies = [ publishedDependency ]
                    Set<DependencyConstraint> dependencyConstraints = []
                    Set<ExcludeRule> globalExcludes = []
                    Set<Capability> capabilities = []
                }
            }
            class MyComponentWithVariants extends MySoftwareComponent implements ComponentWithVariants {
                static nestedVariant = new MyComponentWithUsages()

                Set<SoftwareComponent> variants = [ nestedVariant ]
            }

$append
"""

    }

}
