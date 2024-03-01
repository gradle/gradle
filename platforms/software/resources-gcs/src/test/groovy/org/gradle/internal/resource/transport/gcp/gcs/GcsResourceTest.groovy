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
import spock.lang.Specification

class GcsResourceTest extends Specification {

    URI uri = new URI("http://somewhere")

    def "should get metaData"() {
        def lastModified = new DateTime(new Date())
        def md5hash = '5ab81d4c80e1ee62e6f34e7b3271874e'
        def contentType = 'text/plain'
        def storageObject = new StorageObject()
        storageObject.setUpdated(lastModified)
        storageObject.setMd5Hash(md5hash)
        storageObject.setSize(BigInteger.ONE)
        storageObject.setContentType(contentType)
        storageObject.setEtag(Integer.toString(5))

        when:
        def metaData = new GcsResource(Mock(GcsClient), storageObject, uri).getMetaData()

        then:
        metaData != null
        metaData.location == uri
        metaData.lastModified == new Date(lastModified.value)
        metaData.contentLength == BigInteger.ONE.longValue()
        metaData.contentType == contentType
        metaData.etag == Integer.toString(5)
        metaData.sha1 == null
    }
}
