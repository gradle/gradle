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

import groovy.xml.QName
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyExtraInfo
import spock.lang.Specification

class DefaultIvyExtraInfoSpecTest extends Specification {
    def "can add extra info elements" () {
        def DefaultIvyExtraInfoSpec extraInfo = new DefaultIvyExtraInfoSpec()

        when:
        extraInfo.put("http://my.extra.info", "foo", "fooValue")

        then:
        extraInfo.asMap() == [ (new QName("http://my.extra.info", "foo")): "fooValue" ]
    }

    def "asIvyExtraInfo returns a DefaultIvyExtraInfo class" () {
        def DefaultIvyExtraInfoSpec extraInfo = new DefaultIvyExtraInfoSpec()

        expect:
        extraInfo.asIvyExtraInfo().class == DefaultIvyExtraInfo
    }
}
