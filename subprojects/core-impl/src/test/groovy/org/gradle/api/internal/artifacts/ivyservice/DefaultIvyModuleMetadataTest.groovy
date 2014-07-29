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

import spock.lang.Specification

class DefaultIvyModuleMetadataTest extends Specification {
    def "getExtraInfo returns immutable map"() {
        setup:
        def map = ['some': 'value']
        def ivyModuleMetadata = new DefaultIvyModuleMetadata(map, null)

        when:
        ivyModuleMetadata.getExtraInfo().put('new', 'value')

        then:
        thrown(UnsupportedOperationException)
    }

    def "getBranch returns branch" () {
        given:
        def ivyModuleMetadata = new DefaultIvyModuleMetadata([:], 'someBranch')

        expect:
        ivyModuleMetadata.getBranch() == 'someBranch'
    }
}
