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

import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import org.apache.commons.io.IOUtils
import org.gradle.internal.hash.HashValue
import spock.lang.Specification

class S3ResourceConnectorTest extends Specification {

    public static final String SHA1_STRING = "06e7d22787ee800ce4c9a2b5e94805aee4d7f1f9"
    URI uri = new URI("http://somewhere")

    def "should list resources"() {
        S3Client s3Client = Mock()
        when:
        new S3ResourceConnector(s3Client).list(uri)
        then:
        1 * s3Client.list(uri)
    }

    def "should get a resource"() {
        ObjectMetadata objectMetadata = Mock()
        S3Client s3Client = Mock {
            1 * getResource(uri) >> Mock(S3Object) {
                getObjectMetadata() >> objectMetadata
            }
        }
        when:
        S3Resource s3Resource = new S3ResourceConnector(s3Client).getResource(uri)
        then:
        s3Resource.getURI() == uri
    }

    def "should get a resource sha1"() {
        given:
        URI shaUri = new URI(uri.toString() + ".sha1")
        S3Object s3Object = Mock()
        S3ObjectInputStream s3ObjectInputStream = new S3ObjectInputStream(IOUtils.toInputStream(SHA1_STRING), null)
        s3Object.getObjectContent() >> s3ObjectInputStream

        S3Client s3Client = Mock()
        1 * s3Client.getResource(_) >> { URI u ->
            assert u == shaUri
            return s3Object
        }

        when:
        S3ResourceConnector connector = new S3ResourceConnector(s3Client)
        HashValue sha1 = connector.getResourceSha1(uri)

        then:
        sha1.asHexString() == SHA1_STRING.substring(1, SHA1_STRING.length())
    }

    def "should return a null resource sha1 when sha cannot be read from input stream"() {
        given:
        S3Object s3Object = Mock()
        s3Object.getObjectContent() >> Mock(S3ObjectInputStream)

        S3Client s3Client = Mock()
        1 * s3Client.getResource(_) >> s3Object

        when:
        S3ResourceConnector connector = new S3ResourceConnector(s3Client)
        HashValue sha1 = connector.getResourceSha1(uri)

        then:
        sha1 == null
    }
}
