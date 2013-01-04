/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.externalresource

import org.gradle.api.internal.externalresource.cached.CachedExternalResource
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceAdapter
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.hash.HashUtil
import org.gradle.util.hash.HashValue
import org.junit.Rule
import spock.lang.Specification

public class CachedExternalResourceAdapterTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    ExternalResourceAccessor accessor = Mock()
    CachedExternalResource cachedExternalResource = Mock()
    CachedExternalResourceAdapter cachedResource
    def origin = tmpDir.file('origin')
    def destination = tmpDir.file('destination')
    def download = tmpDir.file('download')

    def setup() {
        cachedExternalResource.cachedFile >> origin
        cachedExternalResource.sha1 >> { HashUtil.createHash(origin, "SHA1") }
        cachedResource = new CachedExternalResourceAdapter("resource-source", cachedExternalResource, accessor)
    }

    def "delegates to cached artifact"() {
        given:
        cachedExternalResource.contentLength >> 22
        cachedExternalResource.externalResourceMetaData >> new DefaultExternalResourceMetaData("url")
        cachedExternalResource.externalLastModifiedAsTimestamp >> 33

        expect:
        cachedResource.contentLength == 22
        cachedResource.lastModified == 33
    }

    def "will copy cache file to destination"() {
        given:
        origin << "some content"

        when:
        cachedResource.writeTo(destination)

        then:
        destination.assertIsCopyOf(origin)
    }

    def "will copy download resource if destination does not match original sha1 after copy"() {
        given:
        origin << "some content"
        download << "some other content"
        ExternalResource resource = Mock()

        when:
        cachedResource.writeTo(destination)

        then:
        cachedExternalResource.cachedFile >> origin
        cachedExternalResource.sha1 >> new HashValue("1234")

        and:
        accessor.getResource("resource-source") >> resource
        resource.writeTo(destination)
    }
}
