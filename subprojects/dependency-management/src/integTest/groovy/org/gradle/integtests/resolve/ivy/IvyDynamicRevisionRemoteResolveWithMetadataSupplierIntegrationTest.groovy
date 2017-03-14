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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.server.http.IvyHttpModule

class IvyDynamicRevisionRemoteResolveWithMetadataSupplierIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test' "

        resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
        withMetadataSupplier()
    }

    def "can use a custom metadata provider"() {
        given:
        buildFile << """
            dependencies {
                compile group: "group", name: "projectA", version: "1.+"
                compile group: "group", name: "projectB", version: "latest.release"
            }

          import javax.inject.Inject
     
          class MP implements ComponentMetadataSupplier {
          
            final RepositoryResourceAccessor repositoryResourceAccessor
            
            @Inject
            MP(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }
          
            int count
          
            void supply(ComponentMetadataSupplierDetails details) {
                def id = details.id
                println "Providing metadata for \$id"
                repositoryResourceAccessor.withResource("\${id.group}/\${id.module}/\${id.version}/status.txt") {
                    details.result.status = new String(it.bytes)
                }
                println "Metadata rule call count: \${++count}"
            }
          }
"""
        when:
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()
        ivyHttpRepo.module("group", "projectA", "2.0").publish()


        and:
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
        outputContains 'Providing metadata for group:projectB:2.2'
        outputContains 'Providing metadata for group:projectB:1.1'
        !output.contains('Providing metadata for group:projectA:1.1')

        and: "creates a new instance of rule each time"
        !output.contains('Metadata rule call count: 2')
    }

    def "handles errors in a custom metadata provider"() {
        given:
        buildFile << """
            
            dependencies {
                compile group: "group", name: "projectA", version: "1.+"
                compile group: "group", name: "projectB", version: "latest.release"
            }

          class MP implements ComponentMetadataSupplier {
          
            void supply(ComponentMetadataSupplierDetails details) {
                throw new NullPointerException("meh: error from custom rule")
            }
          }
"""
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        when:
        expectListVersions(projectA1)
        projectA1.ivy.expectGet()
        expectListVersions(projectB1)

        fails 'checkDeps'

        then:

        errorOutput.contains('Could not resolve group:projectB:latest.release')
        failure.assertHasCause('meh: error from custom rule')
    }

    def "custom metadata provider doesn't have to do something"() {
        given:
        buildFile << """
            
            dependencies {
                compile group: "group", name: "projectA", version: "1.+"
                compile group: "group", name: "projectB", version: "latest.integration"
            }

          class MP implements ComponentMetadataSupplier {
          
            void supply(ComponentMetadataSupplierDetails details) {
                // does nothing
            }
          }
"""
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        when:
        expectGetDynamicRevision(projectA1)
        expectGetDynamicRevision(projectB1)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.1",
            "group:projectB:latest.integration": "group:projectB:1.1"
    }

    def "can use a single remote request to get status of multiple components"() {
        given:
        buildFile << """
          
            dependencies {
                compile group: "group", name: "projectA", version: "1.+"
                compile group: "group", name: "projectB", version: "latest.release"
            }

          import javax.inject.Inject
     
          class MP implements ComponentMetadataSupplier {
          
            final RepositoryResourceAccessor repositoryResourceAccessor
            
            @Inject
            MP(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }
            
            int calls
            Map<String, String> status = [:]
          
            void supply(ComponentMetadataSupplierDetails details) {
                def id = details.id
                println "Providing metadata for \$id"
                repositoryResourceAccessor.withResource("status.txt") {
                    if (status.isEmpty()) {
                        println "Parsing status file call count: \${++calls}"
                        it.withReader { reader ->
                            reader.eachLine { line ->
                                if (line) {
                                   def (module, st) = line.split(';')
                                   status[module] = st
                                }
                            }
                        }
                        println status
                    }
                }
                details.result.status = status[id.toString()]
            }
          }
"""
        when:
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()
        ivyHttpRepo.module("group", "projectA", "2.0").publish()


        and:
        def statusFile = temporaryFolder.createFile("versions.status")
        statusFile << '''group:projectA:1.1;release
group:projectA:1.2;release
group:projectB:1.1;release
group:projectB:2.2;integration
'''
        server.expectGet("/repo/status.txt", statusFile)
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
        outputContains 'Providing metadata for group:projectB:2.2'
        outputContains 'Providing metadata for group:projectB:1.1'
        !output.contains('Providing metadata for group:projectA:1.1')

        and: "remote status file parsed only once"
        outputContains 'Parsing status file call count: 1'
        !output.contains('Parsing status file call count: 2')

        when: "resolving the same dependencies"
        // the following 2 HEAD requests document the current behavior, not necessarily what
        // we want in the end. There are 2 HEAD requests because the file was cached in a previous
        // build, and we're getting the resource twice (once for each module) in this build
        server.expectHead("/repo/status.txt", statusFile)
        server.expectHead("/repo/status.txt", statusFile)

        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        then: "should parse the result from cache"
        output.contains('Parsing status file call count: 1')

        when: "force refresh dependencies"
        executer.withArgument("-PrefreshDynamicVersions")
        statusFile.text = '''group:projectA:1.1;release
group:projectA:1.2;release
group:projectB:1.1;release
group:projectB:2.2;release
'''
        // Similarly the HEAD request here is due to revalidating the cached resource
        server.expectHead("/repo/status.txt", statusFile)
        server.expectGet("/repo/status.txt", statusFile)
        expectListVersions(projectA2)
        expectGetDynamicRevision(projectB2)

        then: "shouldn't use the cached resource"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:2.2"
        outputContains 'Providing metadata for group:projectB:2.2'
        !output.contains('Providing metadata for group:projectB:1.1')
        !output.contains('Providing metadata for group:projectA:1.1')

    }


    def withMetadataSupplier() {
        buildFile << """
          repositories {
              ivy {
                  name 'repo'
                  url '${ivyHttpRepo.uri}'
                  metadataSupplier(MP)
              }
          }
          
          if (project.hasProperty('refreshDynamicVersions')) {
                configurations.all {
                    resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
                }
          }
          
          configurations {
             compile
          }
            
          """
    }

    def checkResolve(Map edges) {
        assert succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                edges.each {from, to ->
                    edge(from, to)
                }
            }
        }
        true
    }

    def expectGetDynamicRevision(IvyHttpModule module) {
        expectListVersions(module)
        module.ivy.expectGet()
        module.jar.expectGet()
    }

    private expectListVersions(IvyHttpModule module) {
        module.repository.directoryList(module.organisation, module.module).expectGet()
    }

    def expectGetStatusOf(IvyHttpModule module, String status = 'release') {
        def file = temporaryFolder.createFile("cheap-${module.version}.status")
        file << status
        server.expectGet("/repo/${module.organisation}/${module.module}/${module.version}/status.txt", file)
    }

    def useRepository(Repository... repo) {
        buildFile << """
repositories {
"""
        repo.each {
            buildFile << "ivy { url '${it.uri}' }\n"
        }
        buildFile << """
}
"""
    }

}
