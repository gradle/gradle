/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class LockEntryFilterFactoryTest extends Specification {

    def "filters #filteredValues and accept #acceptedValues for filter with #filters"() {
        when:
        def filter = LockEntryFilterFactory.forParameter(filters, "Update lock", true)

        then:
        filteredValues.each {
            assert filter.isSatisfiedBy(id(it))
        }
        if (!acceptedValues.empty) {
            acceptedValues.each {
                assert !filter.isSatisfiedBy(id(it))
            }
        }

        where:
        filters                 | filteredValues                    | acceptedValues
        ['org:foo,com*:bar']    | ['org:foo:2.1', 'com:bar:1.1']    | ['org:baz:1.1']
        ['org:foo']             | ['org:foo:2.1']                   | ['com:bar:1.1']
        ['org:foo,']            | ['org:foo:2.1']                   | ['com:bar:1.1'] // Simply shows a trailing comma is ignored
        ['co*:ba*']             | ['com:bar:2.1']                   | ['org:foo:1.1']
        ['*:ba*']               | ['org:bar:2.1']                   | ['com:foo:1.1']
        ['*:bar']               | ['org:bar:2.1']                   | ['com:foo:1.1']
        ['org:f*']              | ['org:foo:1.1']                   | ['com:bar:2.1']
        ['org:*']               | ['org:foo:1.1']                   | ['com:bar:2.1']
        ['or*:*']               | ['org:foo:1.1']                   | ['com:bar:2.1']
        ['*:*']                 | ['org:foo:1.1', 'com:bar:2.1']    | []
    }

    private static ModuleComponentIdentifier id(String notation) {
        String[] parts = notation.split(':')
        DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(parts[0], parts[1]), parts[2])
    }

    def "fails for invalid filter #filters"() {
        when:
        LockEntryFilterFactory.forParameter(filters, "Update lock", true)

        then:
        thrown(IllegalArgumentException)

        where:
        filters << [['*org:foo'], ['org:*foo'], ['or*g:foo'], ['org:fo*o'], ['org'], [',org:foo'], [','], ['org:foo:1.0']]
    }
}
