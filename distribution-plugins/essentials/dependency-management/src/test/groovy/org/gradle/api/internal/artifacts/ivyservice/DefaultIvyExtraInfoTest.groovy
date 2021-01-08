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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

import javax.xml.namespace.QName

class DefaultIvyExtraInfoTest extends Specification {
    def "can get value by name only" () {
        given:
        def extraInfo = new DefaultIvyExtraInfo([ (new NamespaceId('http://my.extra.info', 'foo')): 'fooValue' ])

        expect:
        extraInfo.get('foo') == 'fooValue'
        extraInfo.get('goo') == null
    }

    def "throws exception when name matches multiple keys" () {
        given:
        def extraInfo = new DefaultIvyExtraInfo([
                (new NamespaceId('http://my.extra.info', 'foo')): 'fooValue',
                (new NamespaceId('http://some.extra.info', 'foo')): 'anotherValue',
                (new NamespaceId('http://my.extra.info', 'bar')): 'barValue'
        ])

        when:
        extraInfo.get('foo')

        then:
        def e = thrown(InvalidUserDataException)
        e.message.equals('Cannot get extra info element named \'foo\' by name since elements with this name were found from multiple namespaces (http://my.extra.info, http://some.extra.info).  Use get(String namespace, String name) instead.')
    }

    def "can get by name and namespace" () {
        given:
        def extraInfo = new DefaultIvyExtraInfo([
                (new NamespaceId('http://my.extra.info', 'foo')): 'fooValue',
                (new NamespaceId('http://some.extra.info', 'foo')): 'anotherValue'
        ])

        expect:
        extraInfo.get('http://my.extra.info', 'foo') == 'fooValue'
        extraInfo.get('http://my.extra.info', 'goo') == null
        extraInfo.get('http://other', 'foo') == null
    }

    def "can get extra info values as map" () {
        given:
        def extraInfo = new DefaultIvyExtraInfo([
                (new NamespaceId('http://my.extra.info', 'foo')): 'fooValue',
                (new NamespaceId('http://some.extra.info', 'foo')): 'anotherValue',
                (new NamespaceId('http://my.extra.info', 'bar')): 'barValue'
        ])

        expect:
        extraInfo.asMap() == [
                (new QName('http://my.extra.info', 'foo')): 'fooValue',
                (new QName('http://some.extra.info', 'foo')): 'anotherValue',
                (new QName('http://my.extra.info', 'bar')): 'barValue'
        ]
    }

    def "map view is immutable" () {
        given:
        def extraInfo = new DefaultIvyExtraInfo([ (new NamespaceId('http://my.extra.info', 'foo')): 'fooValue' ])

        when:
        extraInfo.asMap().put(new QName('http://some.extra.info', 'bar'), 'barValue')

        then:
        thrown(UnsupportedOperationException)
    }
}
