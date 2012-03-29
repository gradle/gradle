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

package org.gradle.api.internal.externalresource.metadata

import org.gradle.internal.Factory
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll

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

    @Unroll "always unchanged with incomplete local metadata"() {
        given:
        configureMetadata(local, lastModified, contentLength)

        when:
        compare(local, factory)

        then:
        !unchanged
        0 * factory.create()

        where:
        lastModified | contentLength
        null         | -1
        now          | -1
        null         | -1
    }

    @Unroll "always unchanged with incomplete remote metadata"() {
        given:
        configureMetadata(local)
        configureMetadata(remote, lastModified, contentLength)

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
        configureMetadata(remote)

        when:
        compare(local, remote)

        then:
        unchanged
    }

    def configureMetadata(ExternalResourceMetaData metaData, Date lastModified = now, long contentLength = 100) {
        interaction {
            1 * metaData.getLastModified() >> lastModified
            if (lastModified) {
                1 * metaData.getContentLength() >> contentLength    
            } else {
                0 * metaData.getContentLength()
            }
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
