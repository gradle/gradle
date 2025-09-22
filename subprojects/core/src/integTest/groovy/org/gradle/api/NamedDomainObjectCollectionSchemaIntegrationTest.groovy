/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class NamedDomainObjectCollectionSchemaIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile """
            class SchemaUtils {
                static Map extractSchema(container) {
                    return container.collectionSchema.elements.collectEntries { e ->
                        [ e.name, e.publicType.simpleName ]
                    }.sort()
                }

                static def assertSchemaIs(Map expected, NamedDomainObjectCollection container) {
                    assertSchemaIs(expected, extractSchema(container))
                }

                static def assertSchemaIs(Map expected, Map actual) {
                    def sortedExpected = expected.sort()
                    assert sortedExpected == actual
                }
            }
        """
    }

    def "collection schema from project.domainObjectContainer is public type"() {
        buildFile """
            interface PubType {}
            abstract class Impl implements PubType, Named {
                String name
                @Inject
                Impl(String name) {
                    this.name = name
                }
            }
            def factory = { name -> project.objects.newInstance(Impl, name) }
            def testContainer = project.objects.domainObjectContainer(PubType, factory)

            testContainer.create("foo")
            testContainer.register("bar")
            testContainer.register("baz").get()

            task assertSchema {
                doLast {
                    SchemaUtils.assertSchemaIs(testContainer,
                        "foo": "PubType",
                        "bar": "PubType",
                        "baz": "PubType",
                    )
                }
            }
        """
        expect:
        succeeds("assertSchema")
    }

    def "built-in container types presents public type in schema"() {
        buildFile """
            apply plugin: 'java'

            repositories {
                maven {}
                ivy {}
            }

            task assertSchema {
                def sourceSetsSchema = provider { SchemaUtils.extractSchema(sourceSets) }
                def repositoriesSchema = provider { SchemaUtils.extractSchema(repositories) }
                def configurationsSchema = provider { SchemaUtils.extractSchema(configurations) }
                doLast {
                    SchemaUtils.assertSchemaIs(sourceSetsSchema.get(),
                        "main": "SourceSet",
                        "test": "SourceSet"
                    )
                    SchemaUtils.assertSchemaIs(repositoriesSchema.get(),
                        // TODO: These should be more specific eventually
                        "maven": "ArtifactRepository",
                        "ivy": "ArtifactRepository"
                    )
                    SchemaUtils.assertSchemaIs(configurationsSchema.get(),
                        'annotationProcessor':'Configuration',
                        'apiElements':'Configuration',
                        'archives':'Configuration',
                        'compileClasspath':'Configuration',
                        'compileOnly':'Configuration',
                        'default':'Configuration',
                        'implementation':'Configuration',
                        'runtimeClasspath':'Configuration',
                        'mainSourceElements':'Configuration',
                        'runtimeElements':'Configuration',
                        'runtimeOnly':'Configuration',
                        'testAnnotationProcessor':'Configuration',
                        'testCompileClasspath':'Configuration',
                        'testResultsElementsForTest':'Configuration',
                        'testCompileOnly':'Configuration',
                        'testImplementation':'Configuration',
                        'testRuntimeClasspath':'Configuration',
                        'testRuntimeOnly':'Configuration'
                    )
                }
            }
        """
        expect:
        succeeds("assertSchema")
    }
}
