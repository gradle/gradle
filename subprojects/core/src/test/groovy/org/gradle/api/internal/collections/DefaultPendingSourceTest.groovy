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

package org.gradle.api.internal.collections

import org.gradle.api.Action
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import spock.lang.Specification

class DefaultPendingSourceTest extends Specification {
    def pending = new DefaultPendingSource()
    def provider1 = Mock(ProviderInternal)
    def provider2 = Mock(ProviderInternal)
    def provider3 = Mock(ProviderInternal)
    def setProvider1 = Mock(CollectionProviderInternal)
    def setProvider2 = Mock(CollectionProviderInternal)

    def setup() {
        pending.onRealize(new Action<CollectionProviderInternal>() {
            @Override
            void execute(CollectionProviderInternal provider) {
                provider.get()
            }
        })
    }

    def "realizes pending elements on flush"() {
        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.addPendingCollection(setProvider1)
        pending.addPendingCollection(setProvider2)
        pending.realizePending()

        then:
        1 * provider1.get() >> 1
        1 * provider2.get() >> 2
        1 * provider3.get() >> 3
        1 * setProvider1.get() >> [4, 5]
        1 * setProvider2.get() >> [6, 7]

        and:
        pending.isEmpty()
    }

    def "realizes only pending elements with a given type"() {
        _ * provider1.getType() >> SomeType.class
        _ * provider2.getType() >> SomeOtherType.class
        _ * provider3.getType() >> SomeType.class
        _ * setProvider1.getElementType() >> SomeOtherType.class
        _ * setProvider2.getElementType() >> SomeType.class

        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.addPendingCollection(setProvider1)
        pending.addPendingCollection(setProvider2)
        pending.realizePending(SomeType.class)

        then:
        1 * provider1.get() >> 1
        1 * provider3.get() >> 3
        1 * setProvider2.get() >> [4, 5]

        and:
        pending.size() == 2
    }

    def "cannot realize pending elements when realize action is not set"() {
        given:
        pending.onRealize(null)

        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.realizePending()

        then:
        thrown(IllegalStateException)
    }

    def "can remove pending elements"() {
        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.addPendingCollection(setProvider1)
        pending.addPendingCollection(setProvider2)
        pending.removePending(provider1)
        pending.removePendingCollection(setProvider2)

        then:
        pending.size() == 3

        when:
        pending.realizePending()

        then:
        0 * provider1.get()
        1 * provider2.get() >> 2
        1 * provider3.get() >> 3
        1 * setProvider1.get() >> [4, 5]
        0 * setProvider2.get()

        and:
        pending.isEmpty()
    }

    def "can clear pending elements"() {
        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.addPendingCollection(setProvider1)
        pending.addPendingCollection(setProvider2)
        pending.clear()

        then:
        pending.isEmpty()

        when:
        pending.realizePending()

        then:
        0 * _
    }

    def "can handle realizing elements that modify the list of pending elements"() {
        def pending1Called = false

        given:
        pending.addPending(provider1)
        pending.onRealize(new Action<ProviderInternal>() {
            @Override
            void execute(ProviderInternal providerInternal) {
                pending1Called = true
                pending.addPending(provider2)
            }
        })

        when:
        pending.realizePending()

        then:
        pending1Called
        !pending.isEmpty()
    }

    class BaseType {}
    class SomeType extends BaseType {}
    class SomeOtherType extends BaseType {}
}
