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

class ResourceMapperTest extends Specification {

    URI uri = new URI("http://elsewhere")

    def "should map uri and storage object to external resource metadata"() {
        def lastModified = new DateTime(new Date())
        def md5hash = '6b53d334d6959ff26af1a376c0fbd8ae'
        def contentType = 'application/octet-stream'
        def storageObject = new StorageObject()
        storageObject.setUpdated(lastModified)
        storageObject.setMd5Hash(md5hash)
        storageObject.setSize(BigInteger.TEN)
        storageObject.setContentType(contentType)
        storageObject.setEtag(Integer.toString(16))

        when:
        def metaData = ResourceMapper.toExternalResourceMetaData(uri, storageObject)

        then:
        metaData != null
        metaData.location == uri
        metaData.lastModified == new Date(lastModified.value)
        metaData.contentLength == BigInteger.TEN.longValue()
        metaData.contentType == contentType
        metaData.etag == Integer.toString(16)
        metaData.sha1 == null
    }
}
