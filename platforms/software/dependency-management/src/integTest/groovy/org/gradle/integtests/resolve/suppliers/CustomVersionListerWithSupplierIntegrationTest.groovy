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

package org.gradle.integtests.resolve.suppliers

import groovy.json.JsonBuilder
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

// we only need to check without Gradle metadata, it doesn't matter
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
class CustomVersionListerWithSupplierIntegrationTest extends AbstractModuleDependencyResolveTest {
    private final static String TEST_A_METADATA = new JsonBuilder(
        [
            [version: '3', attributes: ['org.gradle.status': 'release', 'custom': 'foo']],
            [version: '2', attributes: ['org.gradle.status': 'integration', 'custom': 'bar']],
            [version: '1', attributes: ['org.gradle.status': 'release', 'custom': 'bar']]
        ]
    )
    private final static String TEST_B_METADATA = new JsonBuilder(
        [
            [version: '2', attributes: ['org.gradle.status': 'release', 'custom': 'bar']],
            [version: '1', attributes: ['org.gradle.status': 'release', 'custom': 'foo']]
        ]
    )
    private final static String ALL_METADATA = new JsonBuilder(
        [
            [group:'org', name: 'testA', version: '3', attributes: ['org.gradle.status': 'release', 'custom': 'foo']],
            [group:'org', name: 'testA', version: '2', attributes: ['org.gradle.status': 'integration', 'custom': 'bar']],
            [group:'org', name: 'testA', version: '1', attributes: ['org.gradle.status': 'release', 'custom': 'bar']],
            [group:'org', name: 'testB', version: '2', attributes: ['org.gradle.status': 'release', 'custom': 'bar']],
            [group:'org', name: 'testB', version: '1', attributes: ['org.gradle.status': 'release', 'custom': 'foo']]
        ]
    )

    void "can use the same remote cached external resource to get both version list and module metadata"() {
        def versions = ['org:testA': TEST_A_METADATA,
                        'org:testB': TEST_B_METADATA]
        def supplierInteractions = withPerModuleExternalResourceListerAndSupplier(versions, true)
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
            'org:testB:1'()
            'org:testB:2'()
        }
        buildFile << """
            def customAttr = Attribute.of('custom', String)

            dependencies {
                conf "org:testA:latest.release"
                conf "org:testB:latest.release"
            }

            configurations.conf.attributes.attribute(customAttr, 'bar')
        """

        when:
        supplierInteractions.expectGetMetadata('org', 'testA') // one network request for A
        supplierInteractions.expectGetMetadata('org', 'testB') // and one for B
        repositoryInteractions {
            // only the resolved modules are going to get their metadata files fetched (for variant matching)
            // and no additional network request is performed because we got the attributes from the same
            // cached external resource
            'org:testA:1' {
                expectResolve()
            }
            'org:testB:2' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'

        and:
        outputContains "Listing versions for module testA"
        outputContains "Listed [3, 2, 1] for org:testA"
        outputContains "Supplying metadata for module org:testA:3"
        outputContains "Supplying metadata for module org:testA:2"
        outputContains "Supplying metadata for module org:testA:1"
        outputContains "Listing versions for module testB"
        outputContains "Listed [2, 1] for org:testB"
        outputContains "Supplying metadata for module org:testB:2"
        outputDoesNotContain("Supplying metadata for module org:testB:1")
    }

    void "can use the same remote cached external resource to get both version list and module metadata for all modules at once"() {
        withGlobalExternalResourceListerAndSupplier(ALL_METADATA, true)
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
            'org:testB:1'()
            'org:testB:2'()
        }
        buildFile << """
            def customAttr = Attribute.of('custom', String)

            dependencies {
                conf "org:testA:latest.release"
                conf "org:testB:latest.release"
            }

            configurations.conf.attributes.attribute(customAttr, 'bar')
        """

        when:
        repositoryInteractions {
            // only the resolved modules are going to get their metadata files fetched (for variant matching)
            // and no additional network request is performed because we got the attributes from the same
            // cached external resource
            'org:testA:1' {
                expectResolve()
            }
            'org:testB:2' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'

        and:
        outputContains "Listing versions for module testA"
        outputContains "Listed [3, 2, 1] for org:testA"
        outputContains "Supplying metadata for module org:testA:3"
        outputContains "Supplying metadata for module org:testA:2"
        outputContains "Supplying metadata for module org:testA:1"
        outputContains "Listing versions for module testB"
        outputContains "Listed [2, 1] for org:testB"
        outputContains "Supplying metadata for module org:testB:2"
        outputDoesNotContain("Supplying metadata for module org:testB:1")
    }

    private ListerAndSupplierInteractions withPerModuleExternalResourceListerAndSupplier(Map<String, String> moduleToVersions, boolean logQueries = false) {
        metadataListerClass = 'MyLister'
        metadataSupplierClass = 'MySupplier'
        buildFile << """import groovy.json.JsonSlurper

            class MyLister implements ComponentMetadataVersionLister {

                final RepositoryResourceAccessor repositoryResourceAccessor

                @javax.inject.Inject
                MyLister(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }

                void execute(ComponentMetadataListerDetails details) {
                    def id = details.moduleIdentifier
                    if ($logQueries) { println("Listing versions for module \$id.name") }
                    repositoryResourceAccessor.withResource("\${id.group}/\${id.name}/metadata.json") {
                        def json = new JsonSlurper().parse(it, 'utf-8')
                        def versions = json.collect { it.version }
                        if ($logQueries) { println("Listed \$versions for \$id") }
                        details.listed(versions)
                    }
                }
            }

            class MySupplier implements ComponentMetadataSupplier {

                final RepositoryResourceAccessor repositoryResourceAccessor

                @javax.inject.Inject
                MySupplier(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }

                void execute(ComponentMetadataSupplierDetails details) {
                    def id = details.id
                    if ($logQueries) { println("Supplying metadata for module \$id") }
                    repositoryResourceAccessor.withResource("\${id.group}/\${id.module}/metadata.json") {
                        def json = new JsonSlurper().parse(it, 'utf-8')
                        def version = json.find { it.version == id.version }
                        details.result.attributes { attrs ->
                            version.attributes.each { k, v ->
                                attrs.attribute(Attribute.of(k, String), v)
                            }
                        }
                    }
                }
            }
        """
        def files = [:]
        moduleToVersions.each { module, json ->
            def file = temporaryFolder.createFile("metadata-${module.replace(':', '_')}.json")
            file.setText(json, 'utf-8')
            files[module] = file
        }
        new ExternalResourceListerInteractions(files)
    }

    private ListerAndSupplierInteractions withGlobalExternalResourceListerAndSupplier(String jsonFile, boolean logQueries = false) {
        metadataListerClass = 'MyLister'
        metadataSupplierClass = 'MySupplier'
        buildFile << """import groovy.json.JsonSlurper

            class MyLister implements ComponentMetadataVersionLister {

                final RepositoryResourceAccessor repositoryResourceAccessor

                @javax.inject.Inject
                MyLister(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }

                void execute(ComponentMetadataListerDetails details) {
                    def id = details.moduleIdentifier
                    if ($logQueries) { println("Listing versions for module \$id.name") }
                    repositoryResourceAccessor.withResource("/metadata.json") {
                        def json = new JsonSlurper().parse(it, 'utf-8')
                        def versions = json.findAll { it.name == id.name && it.group == id.group }.collect { it.version }
                        if ($logQueries) { println("Listed \$versions for \$id") }
                        details.listed(versions)
                    }
                }
            }

            class MySupplier implements ComponentMetadataSupplier {

                final RepositoryResourceAccessor repositoryResourceAccessor

                @javax.inject.Inject
                MySupplier(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }

                void execute(ComponentMetadataSupplierDetails details) {
                    def id = details.id
                    if ($logQueries) { println("Supplying metadata for module \$id") }
                    repositoryResourceAccessor.withResource("/metadata.json") {
                        def json = new JsonSlurper().parse(it, 'utf-8')
                        def version = json.find { it.group == id.group && it.name == id.module && it.version == id.version }
                        details.result.attributes { attrs ->
                            version.attributes.each { k, v ->
                                attrs.attribute(Attribute.of(k, String), v)
                            }
                        }
                    }
                }
            }
        """
        def file = temporaryFolder.createFile("metadata-all.json")
        file.setText(jsonFile, 'utf-8')
        server.expectGet("/repo/metadata.json", file)
        new ExternalResourceListerInteractions([:])
    }

    interface ListerAndSupplierInteractions {
        void expectGetMetadata(String group, String module)

        void expectRefresh(String group, String module)
    }


    class ExternalResourceListerInteractions implements ListerAndSupplierInteractions {
        private final Map<String, File> files

        ExternalResourceListerInteractions(Map<String, File> files) {
            this.files = files
        }

        @Override
        void expectGetMetadata(String group, String module) {
            String id = "$group:$module"
            server.expectGet("/repo/${group.replace('.', '/')}/$module/metadata.json", files[id])
        }

        @Override
        void expectRefresh(String group, String module) {
            String id = "$group:$module"
            server.expectHead("/repo/${group.replace('.', '/')}/$module/metadata.json", files[id])
        }
    }


}
