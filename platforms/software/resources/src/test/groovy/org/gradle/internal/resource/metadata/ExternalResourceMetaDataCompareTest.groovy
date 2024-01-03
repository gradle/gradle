/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.metadata

import org.gradle.internal.Factory
import spock.lang.Shared
import spock.lang.Specification

class ExternalResourceMetaDataCompareTest extends Specification {

    @Shared now = new Date()

    def local = Mock(ExternalResourceMetaData)
    def remote = Mock(ExternalResourceMetaData)
    def factory = Mock(Factory)

    def unchanged = false

    def "always changed with no local metadata"() {
        when:
        compare(null, factory)

        then:
        !unchanged
        0 * factory.create()
    }

    def "always unchanged with incomplete local metadata"() {
        given:
        configureMetadata(local, etag, lastModified, contentLength)

        when:
        compare(local, factory)

        then:
        !unchanged
        0 * factory.create()

        where:
        etag | lastModified | contentLength
        null | null         | -1
        null | now          | -1
        null | null         | -1
    }

    def "always unchanged with incomplete remote metadata"() {
        given:
        configureMetadata(local)
        configureMetadata(remote, null, lastModified, contentLength)

        when:
        compare(local, factory)

        then:
        !unchanged
        1 * factory.create() >> remote

        where:
        lastModified | contentLength
        null         | -1
        now          | -1
        null         | -1
    }

    def "always changed with no remote metadata"() {
        given:
        configureMetadata(local)

        when:
        compare(local, factory)

        then:
        !unchanged
        1 * factory.create() >> null
    }

    def "is unchanged if everything is equal"() {
        given:
        configureMetadata(local)
        configureMetadataForEtagMatch(remote)

        when:
        compare(local, remote)

        then:
        unchanged
    }

    def "matching etags are enough to be considered equal"() {
        given:
        configureMetadata(local, "abc", null, -1)
        configureMetadataForEtagMatch(remote, "abc")

        when:
        compare(local, remote)

        then:
        unchanged
    }

    def "non matching etags, no mod date, but matching content length does not match"() {
        given:
        configureMetadata(local, "abc", null, 10)
        configureMetadataForEtagMatch(remote, "cde")

        when:
        compare(local, remote)

        then:
        !unchanged
    }

    def "non matching etags, matching mod date, but different content length does not match"() {
        given:
        configureMetadata(local, "abc", now, -1)
        configureMetadataForEtagMatch(remote, "cde")

        when:
        compare(local, remote)

        then:
        !unchanged
    }

    def configureMetadata(ExternalResourceMetaData metaData, String etag = "abc", Date lastModified = now, long contentLength = 100) {
        interaction {
            1 * metaData.getEtag() >> etag

            1 * metaData.getLastModified() >> lastModified
            if (lastModified != null || etag != null) {
                1 * metaData.getContentLength() >> contentLength
            } else {
                0 * metaData.getContentLength()
            }
        }
    }

    def configureMetadataForEtagMatch(ExternalResourceMetaData metaData, String etag = "abc") {
        interaction {
            1 * metaData.getEtag() >> etag
            0 * metaData.getLastModified()
            0 * metaData.getContentLength()
        }
    }
    boolean compare(ExternalResourceMetaData local, ExternalResourceMetaData remote) {
        compare(local, new Factory() {
            def create() { remote }
        })
    }

    boolean compare(ExternalResourceMetaData local, Factory<ExternalResourceMetaData> remoteFactory) {
        unchanged = ExternalResourceMetaDataCompare.isDefinitelyUnchanged(local, remoteFactory)
        unchanged
    }
}
