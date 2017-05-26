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


package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.resource.ExternalResourceListBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType
import spock.lang.Unroll

class DependencyDownloadBuildOperationsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Unroll
    def "emits events for dependency resolution downloads - chunked: #chunked"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        buildFile << """
            apply plugin: "base"

            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            
            dependencies {
              archives "org.utils:impl:1.3"
            }
            
            println configurations.archives.files
        """

        mavenHttpRepo.server.chunkedTransfer = chunked

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 2
        downloadOps[0].details.location == m.pom.uri.toString()
        downloadOps[0].result.bytesRead == m.pom.file.length()
        downloadOps[1].details.location == m.artifact.uri.toString()
        downloadOps[1].result.bytesRead == m.artifact.file.length()

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps.size() == 2
        metaDataOps[0].details.location == m.pom.uri.toString()
        metaDataOps[1].details.location == m.artifact.uri.toString()

        where:
        chunked << [true, false]
    }

    def "emits events for missing resources"() {
        given:
        def emptyRepo = mavenHttpRepo("empty")
        def missing = emptyRepo.module("org.utils", "impl", "1.3").allowAll()
        missing.rootMetaData.allowGetOrHead()
        def missingDir = emptyRepo.directory("org.utils", "impl")
        missingDir.allowGet()

        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()
        m.rootMetaData.file.delete()
        m.rootMetaData.allowGetOrHead()
        def dir = mavenHttpRepo.directory("org.utils", "impl")
        dir.allowGet()

        buildFile << """
            apply plugin: "base"

            repositories {
                maven { url "${emptyRepo.uri}" }
                maven { url "${mavenHttpRepo.uri}" }
            }
            
            dependencies {
              archives "org.utils:impl:1.+"
            }
            
            println configurations.archives.files
        """

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 4
        downloadOps[0].details.location == missing.rootMetaData.uri.toString()
        downloadOps[0].result.bytesRead == 0
        downloadOps[1].details.location == m.rootMetaData.uri.toString()
        downloadOps[1].result.bytesRead == 0
        downloadOps[2].details.location == m.pom.uri.toString()
        downloadOps[2].result.bytesRead == m.pom.file.length()
        downloadOps[3].details.location == m.artifact.uri.toString()
        downloadOps[3].result.bytesRead == m.artifact.file.length()

        def listOps = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps.size() == 2
        listOps[0].details.location == missingDir.uri.toString()
        listOps[1].details.location == dir.uri.toString()

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps.size() == 2
        metaDataOps[0].details.location == m.pom.uri.toString()
        metaDataOps[1].details.location == m.artifact.uri.toString()

        def listOps2 = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps2.size() == 2
        listOps[0].details.location == missingDir.uri.toString()
        listOps[1].details.location == dir.uri.toString()

        def downloadOps2 = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps2.size() == 2
        downloadOps[0].details.location == missing.rootMetaData.uri.toString()
        downloadOps[0].result.bytesRead == 0
        downloadOps[1].details.location == m.rootMetaData.uri.toString()
        downloadOps[1].result.bytesRead == 0
    }

    def "emits events for dynamic dependency resolution"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()
        m.rootMetaData.file.delete()
        m.rootMetaData.allowGetOrHead()
        def dir = mavenHttpRepo.directory("org.utils", "impl")
        dir.allowGet()

        buildFile << """
            apply plugin: "base"

            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            
            dependencies {
              archives "org.utils:impl:1.+"
            }
            
            println configurations.archives.files
        """

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 3
        downloadOps[0].details.location == m.rootMetaData.uri.toString()
        downloadOps[0].result.bytesRead == 0
        downloadOps[1].details.location == m.pom.uri.toString()
        downloadOps[1].result.bytesRead == m.pom.file.length()
        downloadOps[2].details.location == m.artifact.uri.toString()
        downloadOps[2].result.bytesRead == m.artifact.file.length()

        def listOps = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps.size() == 1
        listOps[0].details.location == dir.uri.toString()

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps.size() == 2
        metaDataOps[0].details.location == m.pom.uri.toString()
        metaDataOps[1].details.location == m.artifact.uri.toString()

        def listOps2 = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps2.size() == 1
        listOps2[0].details.location == dir.uri.toString()

        def downloadOps2 = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps2.size() == 1
        downloadOps2[0].details.location == m.rootMetaData.uri.toString()
        downloadOps2[0].result.bytesRead == 0
    }

}
