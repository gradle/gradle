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
package org.gradle.api.internal.artifacts.ivyservice.filestore

import org.gradle.util.HashUtil
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

public class DefaultCachedArtifactTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()

    def "uses value from origin file"() {
        given:
        def origin = tmpDir.file("origin")
        origin << "some text"

        when:
        def DefaultCachedArtifact cachedArtifact = new DefaultCachedArtifact(origin)

        then:
        cachedArtifact.sha1 == HashUtil.createHashString(origin, 'SHA1')
        cachedArtifact.contentLength == origin.length()
        cachedArtifact.lastModified == origin.lastModified()
    }

    def "sha1 content length and last modified do not change when file is subsequently modified"() {
        given:
        def origin = tmpDir.file("origin")
        origin << "some text"


        when:
        def DefaultCachedArtifact cachedArtifact = new DefaultCachedArtifact(origin)
        def originalSha1 = cachedArtifact.sha1
        def originalContentLength = cachedArtifact.contentLength
        def originalLastModified = cachedArtifact.lastModified

        and:
        origin << "some more text"
        origin.setLastModified(11)

        then:
        cachedArtifact.sha1 != HashUtil.createHashString(origin, 'SHA1')
        cachedArtifact.contentLength != origin.length()
        cachedArtifact.lastModified != origin.lastModified()

        and:
        cachedArtifact.sha1 == originalSha1
        cachedArtifact.contentLength == originalContentLength
        cachedArtifact.lastModified == originalLastModified
    }
}
