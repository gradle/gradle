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

package org.gradle.api.publish.ivy.internal.publication

import org.gradle.api.InvalidUserDataException
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import spock.lang.Specification

import javax.xml.namespace.QName

import static org.gradle.util.TestUtil.objectFactory

class DefaultIvyModuleDescriptorSpecTest extends Specification {
    def spec = objectFactory().newInstance(DefaultIvyModuleDescriptorSpec, objectFactory(), objectFactory().newInstance(IvyPublicationCoordinates))

    def "getExtraInfo returns IvyExtraInfo with immutable map" () {
        when:
        spec.getExtraInfo().asMap().put(new QName('http://some.namespace', 'foo'), 'fooValue')

        then:
        thrown(UnsupportedOperationException)
    }

    def "can add extra info elements" () {
        when:
        spec.extraInfo 'http://some.namespace', 'foo', 'fooValue'

        then:
        spec.getExtraInfo().get('foo') == 'fooValue'
    }

    def "cannot add extra info elements with null values" () {
        when:
        spec.extraInfo namespace, name, 'fooValue'

        then:
        def e = thrown(InvalidUserDataException)
        e.getMessage().startsWith("Cannot add an extra info element with null ")

        where:
        namespace                | name
        null                     | "foo"
        "http://my.extra.info"   | null
    }
}
