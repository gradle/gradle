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

import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry
import org.gradle.internal.Describables
import org.gradle.internal.code.UserCodeSource
import spock.lang.Specification

class PropertyProvenanceRegistryTest extends Specification {
    def "origin-only bindings share records and ignore any ambient location"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")

        when:
        def first = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, "Plugin.java:10")
        def second = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, "Plugin.java:20")

        then:
        first.is(second)
        first.formatFrame() == "at plugin 'example' [explicit source]"
        !registry.capturesLocations()
        registry.failureFor("unknown code", PropertyProvenanceKind.GET, "Plugin.java:30").location == null
    }

    def "locations require provenance enabled as well as a location opt-in"() {
        expect:
        new PropertyProvenanceRegistry(enabled, locations).capturesLocations() == expected

        where:
        enabled | locations | expected
        false   | false     | false
        false   | true      | false
        true    | false     | false
        true    | true      | true
    }

    def "interning does not hide a new shadowed convention from the same origin"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")
        def host = Stub(PropertyHost) {
            tracksPropertyProvenance() >> true
            currentPropertyBinding(_) >> { PropertyProvenanceKind kind -> registry.recordFor(source, kind, null) }
        }
        def property = new DefaultProperty<String>(host, String)
        property.convention(new DefaultProvider<String>({ null }))
        property.setToConvention()
        property.convention("new default")

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.count("at plugin 'example' [convention]") == 2
        failure.message.contains("Shadowed configuration")
    }
}
