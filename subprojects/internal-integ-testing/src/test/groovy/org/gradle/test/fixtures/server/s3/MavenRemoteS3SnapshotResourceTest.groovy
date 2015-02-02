/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.server.s3

import spock.lang.Specification
import spock.lang.Unroll

class MavenRemoteS3SnapshotResourceTest extends Specification {

    @Unroll
    def "creates a snapshot file matching regular expression"() {
        given:
        S3StubServer server = Mock()
        server.getAddress() >> new URI('http://localhost')
        MavenRemoteS3SnapshotResource mavenRemoteS3SnapshotResource = new MavenRemoteS3SnapshotResource(server, null, null, null, null)
        when:
        def pattern = mavenRemoteS3SnapshotResource.fileNamePattern(filePath)
        then:
        pattern == expectedPattern
        mavenGenerated ==~ /$pattern/

        where:
        filePath << [
                'org/gradle/publishS3Test/1.45-SNAPSHOT/publishS3Test-1.45-SNAPSHOT.jar'
        ]
        expectedPattern << [
                'org/gradle/publishS3Test/1.45-SNAPSHOT/publishS3Test-1.45-(\\d{8})\\.(\\d{6})-(\\d+).jar'
        ]
        mavenGenerated << [
                'org/gradle/publishS3Test/1.45-SNAPSHOT/publishS3Test-1.45-20150205.075307-1.jar'
        ]
    }
}
