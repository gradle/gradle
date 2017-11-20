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

package org.gradle.api.publish.ivy

class IvyGradleModuleMetadataPublishIntegrationTest extends AbstractIvyPublishIntegTest {
    def setup() {
        buildFile << """
// TODO - use public APIs when available
class TestComponent implements org.gradle.api.internal.component.SoftwareComponentInternal, ComponentWithVariants {
    String name
    Set usages = []
    Set variants = []
}

class TestUsage implements org.gradle.api.internal.component.UsageContext {
    String name
    Usage usage
    Set dependencies = []
    Set artifacts = []
    AttributeContainer attributes
}

class TestVariant implements org.gradle.api.internal.component.SoftwareComponentInternal {
    String name
    Set usages = []
}

    allprojects {
        configurations { implementation }
    }
"""
    }

    def "generates metadata for component with no variants"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.empty
        module.parsedModuleMetadata.attributes.size() == 1
        module.parsedModuleMetadata.attributes['org.gradle.status'] == 'integration'
    }

    def "publishes ivy status"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from comp
                        descriptor {
                           status = 'milestone'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.empty
        module.parsedModuleMetadata.attributes.size() == 1
        module.parsedModuleMetadata.attributes['org.gradle.status'] == 'milestone'
    }

    def "maps project dependencies"() {
        given:
        settingsFile << """rootProject.name = 'root'
            include 'a', 'b'
"""
        buildFile << """
            allprojects {
                apply plugin: 'ivy-publish'
    
                group = 'group'
                version = '1.0'

                publishing {
                    repositories {
                        ivy { url "${ivyRepo.uri}" }
                    }
                }
            }

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies, 
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from comp
                    }
                }
            }
            
            project(':a') {
                publishing {
                    publications {
                        ivy(IvyPublication) {
                            organisation = 'group.a' 
                            module = 'lib_a'
                            revision = '4.5'
                        }
                    }
                }
            }
            project(':b') {
                publishing {
                    publications {
                        ivy(IvyPublication) {
                            organisation = 'group.b' 
                            module = 'utils'
                            revision = '0.01'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def api = module.parsedModuleMetadata.variant('api')
        api.dependencies[0].coords == 'group.a:lib_a:4.5'
        api.dependencies[1].coords == 'group.b:utils:0.01'
    }

    def "publishes component with strict dependencies"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            def comp = new TestComponent()
            comp.usages.add(new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'), 
                    dependencies: configurations.implementation.allDependencies, 
                    attributes: configurations.implementation.attributes))

            dependencies {
                implementation("org:foo") {
                    version {
                        strictly '1.0'
                    }
                }
                implementation("org:bar:2.0")
            }

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from comp
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.parsedModuleMetadata.variants.size() == 1
        def variant = module.parsedModuleMetadata.variants[0]
        variant.dependencies.size() == 2

        variant.dependencies[0].group == 'org'
        variant.dependencies[0].module == 'foo'
        variant.dependencies[0].version == '1.0'
        variant.dependencies[0].rejectsVersion == [']1.0,)']

        variant.dependencies[1].group == 'org'
        variant.dependencies[1].module == 'bar'
        variant.dependencies[1].version == '2.0'
        variant.dependencies[1].rejectsVersion == []
    }

}
