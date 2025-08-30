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
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

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
        ObjectMetadata objectMetadata = Mock()
        S3Client s3Client = Mock {
            1 * getResource(uri) >> Mock(S3Object) {
                getObjectMetadata() >> objectMetadata
            }
        }

        when:
        def s3Resource = new S3ResourceConnector(s3Client).openResource(name, false, null)

        then:
        s3Resource != null

        cleanup:
        s3Resource?.close()
    }


    def "should call close() on S3Object when getMetaData is called"() {
        S3Object s3object = Mock(S3Object) {
            getObjectMetadata() >> Mock(ObjectMetadata) {
                getLastModified() >> new Date()
            }
        }
        S3Client s3Client = Mock {
            1 * getMetaData(uri) >> s3object
        }

        when:
        new S3ResourceConnector(s3Client).getMetaData(name, false)

        then:
        1 * s3object.close()
    }

}
