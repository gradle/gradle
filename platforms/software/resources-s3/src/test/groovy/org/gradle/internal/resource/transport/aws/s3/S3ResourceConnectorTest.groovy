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

package org.gradle.internal.resource.transport.aws.s3

import org.gradle.internal.resource.ExternalResourceName
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import spock.lang.Specification

/**
 * Unit tests for {@link S3ResourceConnector}.
 */
class S3ResourceConnectorTest extends Specification {
    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    def "should list resources"() {
        S3Client s3Client = Mock()
        when:
        new S3ResourceConnector(s3Client).list(name)
        then:
        1 * s3Client.listDirectChildren(uri)
    }

    def "should get a resource"() {
        S3Client s3Client = Mock()
        def getObjectResponse = GetObjectResponse.builder().build()
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream(getObjectResponse, new ByteArrayInputStream('contents'.getBytes()))
        1 * s3Client.getResource(uri) >> responseInputStream

        when:
        def s3Resource = new S3ResourceConnector(s3Client).openResource(name, false)

        then:
        s3Resource != null

        cleanup:
        s3Resource?.close()
    }

    def "treats a missing Last-Modified as unknown"() {
        S3Client s3Client = Mock()
        1 * s3Client.getMetaData(uri) >> HeadObjectResponse.builder().contentLength(12L).build()

        when:
        def metaData = new S3ResourceConnector(s3Client).getMetaData(name, false)

        then:
        metaData.lastModified == null
        metaData.contentLength == 12L
    }

    def "treats a missing Content-Length as unknown"() {
        S3Client s3Client = Mock()
        1 * s3Client.getMetaData(uri) >> HeadObjectResponse.builder().build()

        when:
        def metaData = new S3ResourceConnector(s3Client).getMetaData(name, false)

        then:
        metaData.contentLength == -1
    }
}
