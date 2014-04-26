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
package org.gradle.api.internal.externalresource.cached

import org.gradle.api.internal.externalresource.ExternalResource
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.hash.HashValue
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

public class CachedExternalResourceAdapterTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    ExternalResourceAccessor accessor = Mock()
    LocallyAvailableResource candidate = Mock()
    def uri = new URI("scheme:thing")
    def origin = tmpDir.file('origin')
    def destination = tmpDir.file('destination')

    def setup() {
        candidate.file >> origin
    }

    def "will copy cache file to destination"() {
        given:
        origin << "some content"

        when:
        def cachedResource = new CachedExternalResourceAdapter(uri, candidate, accessor, null, HashUtil.createHash(origin, "sha1"))
        cachedResource.writeTo(destination)

        then:
        destination.assertIsCopyOf(origin)
    }

    def "will copy download resource if destination does not match original sha1 after copy"() {
        given:
        origin << "some content"
        ExternalResource resource = Mock()

        when:
        def cachedResource = new CachedExternalResourceAdapter(uri, candidate, accessor, null, new HashValue(123.toBigInteger().toByteArray()))
        cachedResource.writeTo(destination)

        then:
        accessor.getResource(uri) >> resource
        resource.writeTo(destination)
    }
}
