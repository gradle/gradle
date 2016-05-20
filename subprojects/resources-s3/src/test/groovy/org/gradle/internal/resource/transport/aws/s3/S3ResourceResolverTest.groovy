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
    def "should resolve all resource names from an AWS objectlisting"() {
        setup:
        ObjectListing objectListing = Mock()
        S3ObjectSummary objectSummary = Mock()
        objectSummary.getKey() >> '/SNAPSHOT/some.jar'

        S3ObjectSummary objectSummary2 = Mock()
        objectSummary2.getKey() >> '/SNAPSHOT/someOther.jar'
        objectListing.getObjectSummaries() >> [objectSummary, objectSummary2]

        objectListing.getCommonPrefixes() >> ['/SNAPSHOT/', '/SNAPSHOT/1.0.8/']
        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveDirectoryResourceNames(objectListing)
        then:
        results == ['SNAPSHOT/', '1.0.8/']

        when:
        results = resolver.resolveFileResourceNames(objectListing)
        then:
        results == ['some.jar', 'someOther.jar']

        when:
        results = resolver.resolveResourceNames(objectListing)
        then:
        results == ['some.jar', 'someOther.jar', 'SNAPSHOT/', '1.0.8/']
    }
}
