/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import spock.lang.Unroll

class IvyMappingToVariantIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        System.properties.setProperty("org.gradle.integtest.force.realize.metadata", "true")
    }

    @Unroll
    def "can add variants for ivy - inherit configuration excludes"() {
        when:
        buildFile << """
            String repoPattern = '[organisation]/[module]/[revision]'
            String ivyPattern = "\${repoPattern}/[module]-[revision]-ivy.[ext]"
            String artiPattern = "\${repoPattern}/[artifact]-[revision](-[classifier]).[ext]"

            repositories {
                ivy {
                    url '${new File(getClass().getResource('/org/gradle/integtests/ivy/IvyMappingToVariantIntegrationTest/ivyRepo').toURI()).canonicalPath}'
                    patternLayout {
                        artifact artiPattern
                        ivy ivyPattern
                        m2compatible = true
                    }
                }
            }

            class IvyVariantDerivationRule implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }

                void execute(ComponentMetadataContext context) {
                     if(context.getDescriptor(IvyModuleDescriptor) == null) {
                        return
                     }

                    context.details.addVariant('runtimeElements', 'default') { // the way it is published, the ivy 'default' configuration is the runtime variant
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        }
                    }
                    context.details.addVariant('apiElements', 'compile') {
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                        }
                    }
                }
            }

            apply plugin: 'java'

            project.dependencies.components.all(IvyVariantDerivationRule)

            dependencies {
                implementation 'foo:my-core:1.0.0'
            }

        """

        and:
        def result = succeeds 'dependencyInsight', '--dependency', 'my-excluded-dependency'

        then:
        result.assertNotOutput('my-excluded-dependency')
    }

}
