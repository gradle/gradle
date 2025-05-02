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

import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import spock.lang.Specification

class S3ResourceResolverTest extends Specification {

    def "should resolve file names"() {
        setup:
        ObjectListing objectListing = Mock()
        objectListing.getPrefix() >> 'root/'
        S3ObjectSummary objectSummary = Mock()
        objectSummary.getKey() >> '/SNAPSHOT/some.jar'

        S3ObjectSummary objectSummary2 = Mock()
        objectSummary2.getKey() >> '/SNAPSHOT/someOther.jar'
        objectListing.getObjectSummaries() >> [objectSummary, objectSummary2]
        objectListing.getCommonPrefixes() >> ['root/SNAPSHOT']

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == ['some.jar', 'someOther.jar', 'SNAPSHOT']
    }

    def "should clean common prefixes"() {
        setup:
        ObjectListing objectListing = Mock()
        S3ObjectSummary objectSummary = Mock()
        objectSummary.getKey() >> '/SNAPSHOT/some.jar'
        objectListing.getPrefix() >> 'root/'
        objectListing.getObjectSummaries() >> [objectSummary]
        objectListing.getCommonPrefixes() >> [prefix]

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == ['some.jar', expected]

        where:
        prefix           | expected
        'root/SNAPSHOT'  | 'SNAPSHOT'
        'root/SNAPSHOT/' | 'SNAPSHOT'
    }

    def "should extract file name from s3 listing"() {
        ObjectListing objectListing = Mock()
        S3ObjectSummary objectSummary = Mock()
        objectSummary.getKey() >> listing
        objectListing.getObjectSummaries() >> [objectSummary]

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == expected

        where:
        listing         | expected
        '/a/b/file.pom' | ['file.pom']
        '/file.pom'     | ['file.pom']
        '/file.pom'     | ['file.pom']
        '/SNAPSHOT/'    | []
        '/SNAPSHOT/bin' | []
        '/'             | []
    }


}
