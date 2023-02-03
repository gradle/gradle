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

package org.gradle.internal.resource.transport.gcp.gcs

import com.google.api.client.util.DateTime
import com.google.api.services.storage.model.StorageObject
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

class GcsResourceConnectorTest extends Specification {

    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    def "should list resources"() {
        GcsClient gcsClient = Mock()
        when:
        new GcsResourceConnector(gcsClient).list(name)
        then:
        1 * gcsClient.list(uri)
    }

    def "should get a resource"() {
        GcsClient gcsClient = Mock {
            1 * getResource(uri) >> new StorageObject()
        }

        when:
        def gcsResource = new GcsResourceConnector(gcsClient).openResource(name, false)

        then:
        gcsResource != null
    }

    def "should get a resource metaData"() {
        def lastModified = new DateTime(new Date())
        def md5hash = '3e25960a79dbc69b674cd4ec67a72c62'
        def contentType = 'application/zip'

        GcsClient gcsClient = Mock(GcsClient) {
            1 * getResource(uri) >> {
                def storageObject = new StorageObject()
                storageObject.setUpdated(lastModified)
                storageObject.setMd5Hash(md5hash)
                storageObject.setSize(BigInteger.TEN)
                storageObject.setContentType(contentType)
                storageObject.setEtag(Integer.toString(1))
            }
        }

        when:
        def metaData = new GcsResourceConnector(gcsClient).getMetaData(name, false)

        then:
        metaData != null
    }
}
