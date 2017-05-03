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
import spock.lang.Unroll

/**
 * This is effectively testing BuildOperationExternalResource mechanics.
 * Dependency resolution is the most important usage of this, so is our integration testing vector.
 */
class DependencyDownloadBuildOperationsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Unroll
    void "emits events for dependency resolution downloads - chunked: #chunked"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        args "--refresh-dependencies"

        buildFile << """
            apply plugin: "base"

            import org.gradle.internal.progress.*
    
            def listener = new BuildOperationListener() {
                @Override
                void started(BuildOperationDescriptor operation, OperationStartEvent startEvent) {
                    println "BUILD OPERATION - STARTED :\$operation.displayName-\$operation.details"      
                }
    
                @Override
                void finished(BuildOperationDescriptor operation, OperationResult result) {
                    println "BUILD OPERATION - FINISHED :\$operation.displayName-\$result.result"
                }
            }
    
            gradle.services.get(BuildOperationService).addListener(listener)
            gradle.buildFinished {
                gradle.services.get(BuildOperationService).removeListener(listener)
            }

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
        def expectedAdvertisedLength = chunked ? -1 : actualFileLength
        output.contains "BUILD OPERATION - STARTED :Download ${mavenHttpRepo.uri}/org/utils/impl/1.3/impl-1.3.pom-DownloadBuildOperationDetails{location=${mavenHttpRepo.uri}/org/utils/impl/1.3/impl-1.3.pom, contentLength=${expectedAdvertisedLength}, contentType='null'}"
        output.contains "BUILD OPERATION - FINISHED :Download ${mavenHttpRepo.uri}/org/utils/impl/1.3/impl-1.3.pom-DownloadBuildOperationDetails.Result{readContentLength=$actualFileLength}"

        where:
        chunked << [true, false]
    }

}
