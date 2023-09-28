/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.projection
import org.gradle.model.Managed

class ListModelProjectionTest extends AbstractCollectionModelProjectionTest<String, List<String>> {

    @Managed
    static interface Internal {
        List<String> getItems()
    }

    @Override
    Class<?> holderType() {
        Internal
    }

    def "can remove using index"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            remove(1)
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['foo', 'baz'])
    }

    def "can add using index"() {
        when:
        mutate {
            add 'foo'
            add(0, 'bar')
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar', 'foo']

    }

    def "can addAll a collection which contains a same element types"() {
        when:
        mutate {
            add 'foo'
            addAll(['bar', 'baz'])
            addAll(1, ['one', 'two'])
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['foo', 'one', 'two', 'bar', 'baz'])
    }

    def "can use set(index, obj)"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            set(0, 'foorepl')
            delegate[1] = 'barrepl'
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['foorepl', 'barrepl', 'baz'])
    }

    def "can query using get(index)"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list.get(1) == 'bar'
        list[2] == 'baz'
    }
}
