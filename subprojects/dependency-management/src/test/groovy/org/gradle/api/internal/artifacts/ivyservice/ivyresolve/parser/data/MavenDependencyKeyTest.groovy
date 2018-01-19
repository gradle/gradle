/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data

import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class MavenDependencyKeyTest extends Specification {
    @Unroll
    def "can compare with other instance (#groupId, #artifactId, #type, #classifier, #scope)"() {
        expect:
        MavenDependencyKey key1 = new MavenDependencyKey('group-one', 'artifact-one', 'jar', 'sources', 'runtime')
        MavenDependencyKey key2 = new MavenDependencyKey(groupId, artifactId, type, classifier, scope)
        strictlyEquals(key1, key2) == equality
        (key1.hashCode() == key2.hashCode()) == hashCode
        (key1.toString() == key2.toString()) == stringRepresentation
        key1.equalsWithoutScope(key2) == equalityWithoutScope
        key2.equalsWithoutScope(key1) == equalityWithoutScope

        where:
        groupId     | artifactId     | type  | classifier | scope     | equality | hashCode | stringRepresentation | equalityWithoutScope
        'group-one' | 'artifact-one' | 'jar' | null       | null      | false    | false    | false                | false
        'group-one' | 'artifact-one' | 'jar' | 'sources'  | null      | false    | false    | false                | true
        'group-one' | 'artifact-one' | 'jar' | 'sources'  | 'compile' | false    | false    | false                 | true
        'group-one' | 'artifact-one' | 'jar' | 'sources'  | 'runtime' | true     | true     | true                 | true
    }

    @Unroll
    def "builds String representation (#groupId, #artifactId, #type, #classifier, #scope)"() {
        expect:
        MavenDependencyKey key = new MavenDependencyKey(groupId, artifactId, type, classifier, scope)
        key.toString() == stringRepresentation

        where:
        groupId     | artifactId     | type  | classifier | scope     | stringRepresentation
        'group-one' | 'artifact-one' | 'jar' | null       | null      | 'group-one:artifact-one:jar'
        'group-one' | 'artifact-one' | 'jar' | 'sources'  | 'runtime' | 'group-one:artifact-one:jar:sources:runtime'
    }
}
