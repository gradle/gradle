/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.collections

import org.gradle.api.Action
import org.gradle.api.specs.Spec
import spock.lang.Specification

class BroadcastingCollectionEventRegisterSpec extends Specification {

    def r = register()
    def added = []
    def removed = []

    protected CollectionEventRegister register() {
        new BroadcastingCollectionEventRegister<>()
    }

    protected CollectionFilter filter(Class type, Closure spec = null) {
        if (spec) {
            new CollectionFilter(type, spec as Spec)
        } else {
            new CollectionFilter(type)
        }
    }

    protected Action a(Closure impl) {
        impl as Action
    }

    def "register actions"() {
        given:
        r.registerAddAction a({ added << it })
        r.registerAddAction a({ added << it + 10 })
        r.registerRemoveAction a({ removed << it })
        r.registerRemoveAction a({ removed << it + 10 })

        when:
        r.addAction.execute 1
        r.removeAction.execute 2

        then:
        added == [1, 11]
        removed == [2, 12]
    }

    def "listener added on filtered adds to root"() {
        given:
        r.registerAddAction a({ added << "root" })

        and:
        def filter = filter(Object)
        def f = r.filtered(filter)
        f.registerAddAction a({ added << "filtered" })

        expect:
        filter.isSatisfiedBy("an object")

        when:
        f.addAction.execute "an object"

        then:
        added == ["root", "filtered"]
    }

    def "filter by type"() {
        given:
        def filter = filter(Number)
        def f = register().filtered(filter)
        r.registerAddAction a({ added << "root" })
        f.registerAddAction a({ added << "filtered" })

        expect:
        !filter.isSatisfiedBy("not a number")

        when:
        r.addAction.execute "not a number"

        then:
        added == ["root"]
    }

    def "filter by type on filtered"() {
        given:
        r.registerAddAction a({ added << "root" })

        def f1 = r.filtered(filter(Number))
        f1.registerAddAction a({ added << "filtering for number" })

        def f2 = r.filtered(filter(Integer))
        f2.registerAddAction a({ added << "filtering for integers" })

        when:
        r.addAction.execute 1.2 // not an integer

        then:
        added == ["root", "filtering for number"]
    }

}
