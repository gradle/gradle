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
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSupplier
import spock.lang.Specification

class DefaultPendingSourceTest extends Specification {
    def pending = new DefaultPendingSource()
    def provider1 = Mock(ProviderInternal)
    def provider2 = Mock(ProviderInternal)
    def provider3 = Mock(ProviderInternal)
    def realize = Mock(Action)

    def setup() {
        pending.onRealize(realize)
        _ * provider1.calculateValue() >> ValueSupplier.Value.of("provider1")
        _ * provider2.calculateValue() >> ValueSupplier.Value.of("provider2")
        _ * provider3.calculateValue() >> ValueSupplier.Value.of("provider3")
    }

    def "realizes pending elements on flush"() {
        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.realizePending()

        then:
        1 * realize.execute("provider1")
        1 * realize.execute("provider2")
        1 * realize.execute("provider3")

        and:
        pending.isEmpty()
    }

    def "realizes only pending elements with a given type"() {
        _ * provider1.getType() >> SomeType.class
        _ * provider2.getType() >> SomeOtherType.class
        _ * provider3.getType() >> SomeType.class

        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.realizePending(SomeType.class)

        then:
        1 * realize.execute("provider1")
        0 * realize.execute("provider2")
        1 * realize.execute("provider3")

        and:
        pending.size() == 1
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
        pending.removePending(provider1)

        then:
        pending.size() == 2

        when:
        pending.realizePending()

        then:
        0 * realize.execute("provider1")
        1 * realize.execute("provider2")
        1 * realize.execute("provider3")

        and:
        pending.isEmpty()
    }

    def "can clear pending elements"() {
        when:
        pending.addPending(provider1)
        pending.addPending(provider2)
        pending.addPending(provider3)
        pending.clear()

        then:
        pending.isEmpty()

        when:
        pending.realizePending()

        then:
        0 * realize.execute()
    }

    class BaseType {}
    class SomeType extends BaseType {}
    class SomeOtherType extends BaseType {}
}
