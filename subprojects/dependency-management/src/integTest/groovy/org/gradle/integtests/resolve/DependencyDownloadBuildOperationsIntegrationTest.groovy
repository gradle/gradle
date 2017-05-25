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
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType
import spock.lang.Unroll

class DependencyDownloadBuildOperationsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Unroll
    void "emits events for dependency resolution downloads - chunked: #chunked"() {
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

}
