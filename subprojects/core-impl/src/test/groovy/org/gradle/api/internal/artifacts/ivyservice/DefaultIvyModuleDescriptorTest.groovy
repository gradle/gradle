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

import javax.xml.namespace.QName
import spock.lang.Specification

class DefaultIvyModuleDescriptorTest extends Specification {
    def "getExtraInfo returns immutable map"() {
        setup:
        def map = [(new NamespaceId('http://my.extra.info', 'some')): 'value']
        def ivyModuleDescriptor = new DefaultIvyModuleDescriptor(map, null, null)

        when:
        ivyModuleDescriptor.getExtraInfo().asMap().put(new QName('namespace', 'new'), 'value')

        then:
        thrown(UnsupportedOperationException)
    }

    def "getBranch returns branch" () {
        given:
        def ivyModuleDescriptor = new DefaultIvyModuleDescriptor([:], 'someBranch', null)

        expect:
        ivyModuleDescriptor.getBranch() == 'someBranch'
    }

    def "getIvyStatus returns status"() {
        given:
        def ivyModuleDescriptor = new DefaultIvyModuleDescriptor([:], null, 'release')

        expect:
        ivyModuleDescriptor.getIvyStatus() == 'release'
    }
}
