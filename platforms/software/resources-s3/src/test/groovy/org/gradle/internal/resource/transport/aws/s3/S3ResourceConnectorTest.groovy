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
        //noinspection GroovyAccessibility
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = software.amazon.awssdk.services.s3.S3Client.builder().region(S3Client.DEFAULT_REGION).build()
        def getObjectResponse = GetObjectResponse.builder().build()
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream(getObjectResponse, new ByteArrayInputStream('contents'.getBytes()))
        def getResourceResponse = new S3Client.GetResourceResponse(amazonS3Client, responseInputStream)
        1 * s3Client.getResource(uri) >> getResourceResponse

        when:
        def s3Resource = new S3ResourceConnector(s3Client).openResource(name, false)

        then:
        s3Resource != null

        cleanup:
        s3Resource?.close()
    }
}
