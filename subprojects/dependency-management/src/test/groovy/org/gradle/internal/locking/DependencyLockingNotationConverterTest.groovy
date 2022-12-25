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

class DependencyLockingNotationConverterTest extends Specification {

    def 'converts lock notation to a ModuleComponentIdentifier'() {
        given:
        def converter = new DependencyLockingNotationConverter()
        def lockEntry = 'org:foo:1.1'

        when:
        def converted = converter.convertFromLockNotation(lockEntry)

        then:
        converted instanceof ModuleComponentIdentifier
        converted.group == 'org'
        converted.module == 'foo'
        converted.version == '1.1'
    }

    def "fails to convert an invalid lock notation: #lockEntry"() {
        when:
        def converter = new DependencyLockingNotationConverter()
        converter.convertFromLockNotation(lockEntry)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "The module notation does not respect the lock file format of 'group:name:version' - received '$lockEntry'"

        where:
        lockEntry << ['invalid', 'invalid:invalid', 'invalid:invalid:invalid:1.0']
    }

    def 'converts a ModuleComponentIdentifier to a lock notation'() {
        given:
        def converter = new DependencyLockingNotationConverter()
        def module = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('org', 'foo'), '1.1')

        when:
        def converted = converter.convertToLockNotation(module)

        then:
        converted == 'org:foo:1.1'
    }
}
