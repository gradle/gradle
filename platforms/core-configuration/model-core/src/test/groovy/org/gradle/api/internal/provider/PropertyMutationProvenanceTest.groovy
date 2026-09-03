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

import org.gradle.api.internal.provider.provenance.ContributorKey
import org.gradle.api.internal.provider.provenance.MutationKind
import org.gradle.api.internal.provider.provenance.MutationOrigin
import org.gradle.api.internal.provider.provenance.MutationOriginRegistry
import org.gradle.api.internal.provider.provenance.MutationRecord
import org.gradle.api.internal.provider.provenance.MutationTrace
import org.gradle.internal.Describables
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

class PropertyMutationProvenanceTest extends Specification {

    static final UserCodeSource PLUGIN_WITH_ID =
        new UserCodeSource.Binary(Describables.of("plugin 'com.example.feature'"), "com.example.FeaturePlugin", "com.example.feature")
    static final UserCodeSource PLUGIN_BY_CLASS =
        new UserCodeSource.Binary(Describables.of("class com.example.FeaturePlugin"), "com.example.FeaturePlugin", null)
    static final UserCodeSource BUILD_SCRIPT =
        new UserCodeSource.Script(Describables.of("build file 'build.gradle'"), URI.create("file:/p/build.gradle"), true)
    static final UserCodeSource SCRIPT_PLUGIN =
        new UserCodeSource.Script(Describables.of("script 'other.gradle'"), URI.create("file:/p/other.gradle"), false)

    def registry = new MutationOriginRegistry(true)
    def host = new TrackingPropertyHost(registry: registry)

    def "derives contributor identity from #source"() {
        expect:
        ContributorKey.of(source).kind == kind
        ContributorKey.of(source).id == id

        where:
        source           | kind                              | id
        PLUGIN_WITH_ID   | ContributorKey.Kind.PLUGIN        | "com.example.feature"
        PLUGIN_BY_CLASS  | ContributorKey.Kind.PLUGIN_CLASS  | "com.example.FeaturePlugin"
        BUILD_SCRIPT     | ContributorKey.Kind.BUILD_AUTHOR  | ""
        SCRIPT_PLUGIN    | ContributorKey.Kind.SCRIPT_PLUGIN | "file:/p/other.gradle"
        null             | ContributorKey.Kind.UNKNOWN       | ""
    }

    def "all build scripts are the same contributor, distinct script plugins are not"() {
        def otherScript = new UserCodeSource.Script(Describables.of("build file 'lib/build.gradle'"), URI.create("file:/p/lib/build.gradle"), true)
        def otherPlugin = new UserCodeSource.Script(Describables.of("script 'more.gradle'"), URI.create("file:/p/more.gradle"), false)

        expect:
        ContributorKey.of(BUILD_SCRIPT) == ContributorKey.of(otherScript)
        ContributorKey.of(SCRIPT_PLUGIN) != ContributorKey.of(otherPlugin)
    }

    def "records for the same source and kind are interned"() {
        expect:
        registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE).is(registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE))
        !registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE).is(registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_CONVENTION))
        !registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE).is(registry.recordFor(BUILD_SCRIPT, MutationKind.SET_SOURCE))
    }

    def "an unattributed mutation is recorded as unknown"() {
        def record = registry.recordFor(null, MutationKind.SET_SOURCE)

        expect:
        record.origin.contributor == ContributorKey.UNKNOWN
        !record.attributed
    }

    def "records who set the value and who set the convention"() {
        def property = new DefaultProperty<String>(host, String)

        when:
        host.source = PLUGIN_WITH_ID
        property.convention("default")
        host.source = BUILD_SCRIPT
        property.set("value")

        then:
        property.conventionMutation.describe() == "given its convention by plugin 'com.example.feature'"
        property.explicitMutation.describe() == "set by build file 'build.gradle'"
    }

    def "records the kind of mutation for #description"() {
        given:
        host.source = PLUGIN_WITH_ID
        def property = create(host)

        when:
        mutate(property)

        then:
        property.explicitMutation?.kind == kind

        where:
        description   | create                                                        | mutate                  | kind
        "set"         | { new DefaultProperty<String>(it, String) }                   | { it.set("a") }         | MutationKind.SET_SOURCE
        "unset"       | { new DefaultProperty<String>(it, String) }                   | { it.unset() }          | MutationKind.UNSET
        "convention"  | { new DefaultProperty<String>(it, String) }                   | { it.convention("a") }  | null
        "list add"    | { new DefaultListProperty<String>(it, String) }               | { it.add("a") }         | MutationKind.ADD
        "list addAll" | { new DefaultListProperty<String>(it, String) }               | { it.addAll(["a"]) }    | MutationKind.ADD_ALL
        "map put"     | { new DefaultMapProperty<String, String>(it, String, String) } | { it.put("k", "v") }   | MutationKind.PUT
        "map putAll"  | { new DefaultMapProperty<String, String>(it, String, String) } | { it.putAll([k: "v"]) } | MutationKind.PUT_ALL
    }

    def "a rejected mutation leaves no trace"() {
        def property = new DefaultProperty<String>(host, String)
        host.source = PLUGIN_WITH_ID
        property.set("first")
        property.finalizeValue()

        when:
        host.source = BUILD_SCRIPT
        property.set("second")

        then:
        thrown(IllegalStateException)
        property.explicitMutation.origin.contributor == ContributorKey.of(PLUGIN_WITH_ID)
    }

    def "a rejected mutation says who configured the property"() {
        def property = new DefaultProperty<String>(host, String)
        host.source = PLUGIN_WITH_ID
        property.set("first")
        property.finalizeValue()

        when:
        property.set("second")

        then:
        def e = thrown(IllegalStateException)
        e.message == "The value for this property is final and cannot be changed any further. It was last set by plugin 'com.example.feature'."
    }

    def "a missing value says who configured the property"() {
        def property = new DefaultProperty<String>(host, String)
        host.source = PLUGIN_WITH_ID
        property.set(Providers.notDefined())

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this property because it has no value available.\nThis property was last set by plugin 'com.example.feature'."
    }

    def "messages are unchanged when the mutation cannot be attributed"() {
        def property = new DefaultProperty<String>(host, String)
        property.set(Providers.notDefined())

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this property because it has no value available."
    }

    def "records nothing when provenance is disabled"() {
        def disabledHost = new TrackingPropertyHost(registry: new MutationOriginRegistry(false), source: PLUGIN_WITH_ID)
        def property = new DefaultProperty<String>(disabledHost, String)

        when:
        property.set("value")
        property.finalizeValue()
        property.set("other")

        then:
        def e = thrown(IllegalStateException)
        e.message == "The value for this property is final and cannot be changed any further."
        property.explicitMutation == null
    }

    def "reports the chain when a property was configured more than once"() {
        def property = new DefaultProperty<String>(host, String)

        when:
        host.source = PLUGIN_WITH_ID
        property.convention("default")
        host.source = SCRIPT_PLUGIN
        property.set("from script plugin")
        host.source = BUILD_SCRIPT
        property.set("from build script")
        property.finalizeValue()
        property.set("too late")

        then:
        def e = thrown(IllegalStateException)
        e.message == """The value for this property is final and cannot be changed any further.
It was configured by:
  given its convention by plugin 'com.example.feature'
  -> set by script 'other.gradle'
  -> set by build file 'build.gradle'"""
    }

    def "reports the chain when a property with no value is queried"() {
        def property = new DefaultProperty<String>(host, String)

        when:
        host.source = PLUGIN_WITH_ID
        property.set("value")
        host.source = BUILD_SCRIPT
        property.set(Providers.notDefined())
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == """Cannot query the value of this property because it has no value available.
This property was configured by:
  set by plugin 'com.example.feature'
  -> set by build file 'build.gradle'"""
    }

    def "bounds the retained trace"() {
        def property = new DefaultProperty<String>(host, String)
        host.source = PLUGIN_WITH_ID

        when:
        40.times { property.set("value $it") }

        then:
        property.mutationHistory.records.size() == 32
        property.mutationHistory.notRetainedCount == 8
        property.mutationHistory.describeForMessage().endsWith("-> and 8 later mutation(s) not retained.")
    }

    def "a single mutation is held without allocating a trace"() {
        def property = new DefaultProperty<String>(host, String)
        host.source = PLUGIN_WITH_ID

        when:
        property.set("once")

        then:
        // the interned record itself is the history: no per-property allocation
        property.mutationHistory.is(registryRecord(MutationKind.SET_SOURCE))
        property.mutationHistory.records == [registryRecord(MutationKind.SET_SOURCE)]

        when:
        property.set("twice")

        then:
        property.mutationHistory instanceof MutationTrace
        property.mutationHistory.records.size() == 2
    }

    private MutationRecord registryRecord(MutationKind kind) {
        host.registry.recordFor(PLUGIN_WITH_ID, kind)
    }

    def "renders the call site when a record carries one"() {
        def record = new MutationRecord(MutationOrigin.of(PLUGIN_WITH_ID), MutationKind.SET_SOURCE, "FeaturePlugin.groovy:42")

        expect:
        record.describe() == "set by plugin 'com.example.feature' at FeaturePlugin.groovy:42"
    }

    def "a located record is allocated rather than interned"() {
        expect:
        registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE, null)
            .is(registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE))
        !registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE, "build.gradle:1")
            .is(registry.recordFor(PLUGIN_WITH_ID, MutationKind.SET_SOURCE, "build.gradle:1"))
    }

    static class TrackingPropertyHost implements PropertyHost {
        MutationOriginRegistry registry = new MutationOriginRegistry(true)
        UserCodeSource source

        @Override
        String beforeRead(ModelObject producer) {
            return null
        }

        @Override
        boolean tracksMutationProvenance() {
            return registry.enabled
        }

        @Override
        MutationRecord currentMutation(MutationKind kind) {
            return registry.recordFor(source, kind)
        }
    }
}
