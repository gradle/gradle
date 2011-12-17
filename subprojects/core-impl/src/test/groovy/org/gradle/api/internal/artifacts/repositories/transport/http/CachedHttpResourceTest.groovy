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
package org.gradle.api.internal.artifacts.repositories.transport.http;


import org.apache.ivy.util.ChecksumHelper
import org.apache.ivy.util.CopyProgressListener
import org.gradle.api.internal.artifacts.ivyservice.filestore.CachedArtifact
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

public class CachedHttpResourceTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()

    HttpResourceCollection httpResourceCollection = Mock()
    CachedArtifact cachedArtifact = Mock()
    CopyProgressListener progress = Mock()
    CachedHttpResource cachedResource = new CachedHttpResource("resource-source", cachedArtifact, httpResourceCollection)
    def origin = tmpDir.file('origin')
    def destination = tmpDir.file('destination')
    def download = tmpDir.file('download')

    def "delegates to cached artifact"() {
        given:
        cachedArtifact.contentLength >> 22
        cachedArtifact.lastModified >> 33

        expect:
        cachedResource.contentLength == 22
        cachedResource.lastModified == 33
    }

    def "will copy cache file to destination"() {
        given:
        origin << "some content"

        when:
        cachedResource.writeTo(destination, progress)

        then:
        cachedArtifact.origin >> origin
        cachedArtifact.sha1 >> ChecksumHelper.computeAsString(origin, "sha1")

        and:
        destination.assertIsCopyOf(origin)
    }

    def "will copy download resource if destination does not match original sha1 after copy"() {
        given:
        origin << "some content"
        download << "some other content"
        HttpResource resource = Mock()

        when:
        cachedResource.writeTo(destination, progress)

        then:
        cachedArtifact.origin >> origin
        cachedArtifact.sha1 >> "different"

        and:
        httpResourceCollection.getResource("resource-source") >> resource
        resource.writeTo(destination, progress)
    }
}
