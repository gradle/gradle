/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.ValueSupplier
import spock.lang.Specification

abstract class PendingSourceSpec extends Specification {

    abstract PendingSource<CharSequence> getSource()

    def "does not run side effects of pending providers when realizing pending elements"() {
        given:
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def sideEffect3 = Mock(ValueSupplier.SideEffect)
        def sideEffect4 = Mock(ValueSupplier.SideEffect)
        def sideEffect5 = Mock(ValueSupplier.SideEffect)
        def propertyFactory = new DefaultPropertyFactory(Stub(PropertyHost))
        def source = getSource()

        when:
        def provider1 = Providers.of("v1").withSideEffect(sideEffect1)
        source.addPending(provider1)
        def provider2 = Providers.of("v2").withSideEffect(sideEffect2)
        source.addPending(provider2)
        def provider3 = Providers.of("v3").withSideEffect(sideEffect3)
        source.addPending(provider3)
        def provider4 = propertyFactory.listProperty(String).with {
            it.add(Providers.of("v4").withSideEffect(sideEffect4))
            it
        }
        source.addPendingCollection(provider4)
        def provider5 = propertyFactory.listProperty(String).with {
            it.add(Providers.of("v5").withSideEffect(sideEffect5))
            it
        }
        source.addPendingCollection(provider5)

        then:
        0 * _ // no side effects until elements are realized

        when:
        source.removePending(provider2)
        source.removePendingCollection(provider4)

        then:
        0 * _ // can remove pending without running side effects

        when:
        source.realizePending()

        then:
        0 * sideEffect1.execute("v1")

        then: // ensure ordering
        0 * sideEffect3.execute("v3")

        then: // ensure ordering
        0 * sideEffect5.execute("v5")

        // side effects of removed providers do not execute
        0 * sideEffect2.execute(_)
        0 * sideEffect4.execute(_)

        when:
        source.realizePending()

        then:
        0 * _ // realizing again does not run side effects
    }
}
