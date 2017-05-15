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
import org.gradle.internal.resource.transfer.DownloadBuildOperationDetails
import spock.lang.Unroll

/**
 * This is effectively testing BuildOperationExternalResource mechanics.
 * Dependency resolution is the most important usage of this, so is our integration testing vector.
 */
class DependencyDownloadBuildOperationsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Unroll
    void "emits events for dependency resolution downloads - chunked: #chunked"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        args "--refresh-dependencies", "-S"

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
        def actualFileLength = m.pom.file.bytes.length
        def buildOp = buildOperations.operation(DownloadBuildOperationDetails)
        buildOp.details.contentType == 'null'
        buildOp.details.contentLength == chunked ? -1 : actualFileLength
        buildOp.details.location.path == m.pomPath
        buildOp.result.readContentLength == actualFileLength

        where:
        chunked << [true, false]
    }

}
