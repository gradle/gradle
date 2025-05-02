/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.internal.id

import spock.lang.Specification

class CompositeIdGeneratorTest extends Specification {
    private final IdGenerator<?> target = Mock()
    private final CompositeIdGenerator generator = new CompositeIdGenerator('scope', target)

    def createsACompositeId() {
        def original = 12
        when:
        def id = generator.generateId()

        then:
        id != original
        id.toString() == 'scope.12'

        and:
        1 * target.generateId() >> original
    }

    def compositeIdsAreNotEqualWhenOriginalIdsAreDifferent() {
        when:
        def id = generator.generateId()
        def id2 = generator.generateId()

        then:
        id != id2

        and:
        1 * target.generateId() >> 12
        1 * target.generateId() >> 13
    }

    def compositeIdsAreNotEqualWhenScopesAreDifferent() {
        CompositeIdGenerator other = new CompositeIdGenerator('other', target)

        when:
        def id = generator.generateId()
        def id2 = other.generateId()

        then:
        id != id2

        and:
        2 * target.generateId() >> 12
    }

    def compositeIdsAreEqualWhenOriginalIdsAreEqual() {
        when:
        def id = generator.generateId()
        def id2 = generator.generateId()

        then:
        id == id2

        and:
        2 * target.generateId() >> 12
    }
}


