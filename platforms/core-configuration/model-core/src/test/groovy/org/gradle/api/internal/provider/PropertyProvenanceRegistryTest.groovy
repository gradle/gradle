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
import org.gradle.api.internal.provider.provenance.PropertyProvenanceOrigin
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry
import org.gradle.internal.Describables
import org.gradle.internal.code.UserCodeSource
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier

class PropertyProvenanceRegistryTest extends ConcurrentSpec {
    def "both successful binding kinds are shared for #description"() {
        def registry = new PropertyProvenanceRegistry(true)

        when:
        def explicit = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, null)
        def convention = registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null)

        then:
        explicit.is(registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, null))
        convention.is(registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null))
        explicit.origin.is(convention.origin)
        explicit.kind == PropertyProvenanceKind.EXPLICIT_SOURCE
        convention.kind == PropertyProvenanceKind.CONVENTION

        where:
        description      | source
        "unknown code"   | null
        "a known plugin" | new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")
    }

    def "failed operation #kind cannot be interned as a successful binding"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")

        when:
        registry.recordFor(source, kind, null)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message == "Not a successful binding kind: $kind"
        registry.recordsBySource.isEmpty()

        where:
        kind << PropertyProvenanceKind.values().findAll { it != PropertyProvenanceKind.EXPLICIT_SOURCE && it != PropertyProvenanceKind.CONVENTION }
    }

    def "failure records are per occurrence with locations enabled = #locations"() {
        def registry = new PropertyProvenanceRegistry(true, locations)

        expect:
        PropertyProvenanceKind.values().findAll { it != PropertyProvenanceKind.EXPLICIT_SOURCE && it != PropertyProvenanceKind.CONVENTION }.every { kind ->
            def first = registry.failureFor("plugin 'example'", kind, "Plugin.java:10")
            def second = registry.failureFor("plugin 'example'", kind, "Plugin.java:20")
            !first.is(second) &&
                first.formatFrame() == "at plugin 'example'${locations ? ' (Plugin.java:10)' : ''} [${kind.displayName}]" &&
                second.formatFrame() == "at plugin 'example'${locations ? ' (Plugin.java:20)' : ''} [${kind.displayName}]"
        }
        registry.recordsBySource.isEmpty()

        where:
        locations << [false, true]
    }

    def "concurrent first access publishes one complete binding pair per source"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")
        def results = new CopyOnWriteArrayList()
        def barrier = new CyclicBarrier(10)

        when:
        async {
            10.times { n ->
                start {
                    barrier.await()
                    def firstKind = n % 2 == 0 ? PropertyProvenanceKind.EXPLICIT_SOURCE : PropertyProvenanceKind.CONVENTION
                    registry.recordFor(source, firstKind, null)
                    results.add([
                        registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, null),
                        registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null)
                    ])
                }
            }
        }

        then:
        results.size() == 10
        results.every { pair ->
            pair[0].is(results[0][0]) && pair[1].is(results[0][1]) && pair[0].origin.is(pair[1].origin)
        }
        registry.recordsBySource.size() == 1
    }

    def "plugin identity comes from source metadata rather than its display name"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Binary(Describables.of("a deliberately unrelated label"), "example.Plugin", pluginId)

        when:
        def record = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, null)

        then:
        record.origin.type == type
        record.origin.identifier == identifier
        record.formatFrame() == "at a deliberately unrelated label [explicit source]"

        where:
        pluginId      | type                                      | identifier
        "example.id"  | PropertyProvenanceOrigin.Type.PLUGIN_ID    | "example.id"
        null          | PropertyProvenanceOrigin.Type.PLUGIN_CLASS | "example.Plugin"
    }

    def "operations and locations share an origin descriptor but not their occurrence records"() {
        def registry = new PropertyProvenanceRegistry(true, true)
        def source = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")

        when:
        def convention = registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null)
        def binding = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, "Plugin.java:10")
        def later = registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, "Plugin.java:20")

        then:
        convention.origin.is(binding.origin)
        later.origin.is(binding.origin)
        !binding.is(later)
        binding.location == "Plugin.java:10"
        later.location == "Plugin.java:20"
    }

    def "matching plugin IDs do not merge different application sources"() {
        def registry = new PropertyProvenanceRegistry(true)
        def first = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")
        def second = new UserCodeSource.Binary(Describables.of("plugin 'example'"), "ExamplePlugin", "example")

        when:
        def firstRecord = registry.recordFor(first, PropertyProvenanceKind.EXPLICIT_SOURCE, null)
        def secondRecord = registry.recordFor(second, PropertyProvenanceKind.EXPLICIT_SOURCE, null)

        then:
        firstRecord.origin.identifier == secondRecord.origin.identifier
        !firstRecord.origin.is(secondRecord.origin)
        !firstRecord.is(secondRecord)
    }

    def "script descriptors preserve metadata without guessing a role from the filename"() {
        def registry = new PropertyProvenanceRegistry(true)
        def source = new UserCodeSource.Script(Describables.of("script display name"), uri)

        when:
        def record = registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null)

        then:
        record.origin.type == PropertyProvenanceOrigin.Type.SCRIPT
        record.origin.identifier == identifier
        record.formatFrame() == "at script display name [convention]"

        where:
        uri                                              | identifier
        new URI("file:/build/sub/../settings.gradle.kts") | "file:/build/settings.gradle.kts"
        null                                             | null
    }

    def "unidentified sources are explicitly unknown"() {
        def registry = new PropertyProvenanceRegistry(true)

        expect:
        registry.recordFor(null, PropertyProvenanceKind.CONVENTION, null).origin.is(PropertyProvenanceOrigin.UNKNOWN)
    }

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
