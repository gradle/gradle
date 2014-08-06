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

import javax.xml.namespace.QName
import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

class DefaultIvyModuleDescriptorSpecTest extends Specification {
    def "getExtraInfo returns IvyExtraInfo with immutable map" () {
        def spec = new DefaultIvyModuleDescriptorSpec(Stub(IvyPublicationInternal))

        when:
        spec.getExtraInfo().asMap().put(new QName('http://some.namespace', 'foo'), 'fooValue')

        then:
        thrown(UnsupportedOperationException)
    }

    def "can add extra info elements" () {
        def spec = new DefaultIvyModuleDescriptorSpec(Stub(IvyPublicationInternal))

        when:
        spec.extraInfo 'http://some.namespace', 'foo', 'fooValue'

        then:
        spec.getExtraInfo().get('foo') == 'fooValue'
    }

    def "cannot add extra info elements with null values" () {
        def spec = new DefaultIvyModuleDescriptorSpec(Stub(IvyPublicationInternal))

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
