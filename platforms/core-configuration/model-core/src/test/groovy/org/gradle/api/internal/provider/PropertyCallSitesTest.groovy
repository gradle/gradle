/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provider.provenance.PropertyCallSites
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry
import spock.lang.Specification

class PropertyCallSitesTest extends Specification {
    def "instrumented #operation uses the target property capture mode and restores context after failure"() {
        def host = Stub(PropertyHost) {
            tracksPropertyProvenance() >> true
            capturesPropertyCallSites() >> locations
        }
        def property = new DefaultProperty<Object>(host, Object) {
            @Override
            protected void assertCanMutate() {
                assert PropertyCallSites.current() == (capturesPropertyCallSites() ? "Plugin.java:12" : null)
                // Constructing another build's registry must not change this property's mode.
                new PropertyProvenanceRegistry(false)
                assert PropertyCallSites.current() == (capturesPropertyCallSites() ? "Plugin.java:12" : null)
                throw new IllegalStateException("rejected")
            }
        }

        when:
        mutation(property)

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "rejected"
        PropertyCallSites.current() == null

        where:
        operation      | locations | mutation
        "set(value)"   | false     | { PropertyCallSites.set(it, "value", "Plugin.java:12") }
        "set(value)"   | true      | { PropertyCallSites.set(it, "value", "Plugin.java:12") }
        "set(provider)"| false     | { PropertyCallSites.set(it, Providers.of("value"), "Plugin.java:12") }
        "set(provider)"| true      | { PropertyCallSites.set(it, Providers.of("value"), "Plugin.java:12") }
    }

    def "instrumented conventions capture only for opted-in properties"() {
        def observed = []
        def host = Mock(PropertyHost) {
            tracksPropertyProvenance() >> true
            capturesPropertyCallSites() >> locations
            currentPropertyBinding(_) >> {
                observed << PropertyCallSites.current()
                null
            }
        }
        def property = new DefaultProperty<Object>(host, Object)

        when:
        PropertyCallSites.convention(property, "default", "Plugin.java:10")
        PropertyCallSites.convention(property, Providers.of("default"), "Plugin.java:11")

        then:
        observed == (locations ? ["Plugin.java:10", "Plugin.java:11"] : [null, null])
        PropertyCallSites.current() == null

        where:
        locations << [false, true]
    }
}
