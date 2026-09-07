/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.configuration

import org.gradle.api.internal.provenance.ContributorKey
import org.gradle.api.internal.provenance.DiagnosticOrigin
import org.gradle.api.internal.provider.PropertyProvenanceHost
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.internal.Describables
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.util.Path
import spock.lang.Specification

class PropertyProvenanceRegistryTest extends Specification {
    def context = new DefaultUserCodeApplicationContext()
    def registry = new PropertyProvenanceRegistry(true, context)
    def target = ConfigurationTargetIdentifier.of(ProjectIdentity.forSubproject(Path.ROOT, Path.path(':source')))

    def 'application descriptors are shared and repeated applications keep contributor identity'() {
        given:
        def source = registry.binarySource(Describables.of('plugin'), 'PluginType', 'example', target)
        def records = []

        when:
        context.apply(source) {
            records.add(registry.currentAttribution())
            records.add(registry.currentAttribution())
        }
        context.apply(source) { records.add(registry.currentAttribution()) }

        then:
        records[0].is(records[1])
        records[0].contributor == records[2].contributor
        records[0].applicationToken != records[2].applicationToken
        records[0].contributor.kind == ContributorKey.Kind.PLUGIN_ID
        records[0].sourceScope.scopePath == ':source'
        records[0].origin.kind == DiagnosticOrigin.Kind.PLUGIN_ID
    }

    def 'class fallback and separate build domains never alias matching display labels'() {
        given:
        def other = new PropertyProvenanceRegistry(true, context)
        def sources = [registry.binarySource(Describables.of('same'), 'PluginType', null, target),
                       other.binarySource(Describables.of('same'), 'PluginType', null, target)]
        def records = []

        when:
        sources.each { source -> context.apply(source) { records.add(registry.currentAttribution()) } }

        then:
        records*.contributor*.identity == ['PluginType', 'PluginType']
        records[0].contributor.kind == ContributorKey.Kind.PLUGIN_CLASS
        records[0].contributor != records[1].contributor
        records[0].origin == records[1].origin
        registry.newOccurrenceScope() != other.newOccurrenceScope()
        registry.newOccurrenceScope() != registry.newOccurrenceScope()
    }

    def 'provider creator is not the contributor binding a tracked target'() {
        given:
        def host = Stub(PropertyProvenanceHost) {
            newOccurrenceScope() >> registry.newOccurrenceScope()
            currentAttribution() >> { registry.currentAttribution() }
        }
        def property = new DefaultPropertyFactory(host).property(String)
        def creator = registry.binarySource(Describables.of('creator'), 'Creator', 'creator', target)
        def binder = registry.binarySource(Describables.of('binder'), 'Binder', 'binder', target)
        def calls = 0
        def provider

        when:
        context.apply(creator) { provider = new DefaultProvider({ calls++; 'value' }).map { calls++; it } }
        context.apply(binder) { property.set(provider) }

        then:
        property.lastAcceptedMutation.attribution.contributor.identity == 'binder'
        calls == 0

        when:
        def accepted = property.lastAcceptedMutation
        def value = property.get()

        then:
        value == 'value'
        calls == 2
        property.lastAcceptedMutation.is(accepted)
    }

    def 'script role comes from application boundary even when filename suggests another role'() {
        given:
        def records = []
        def uri = new URI('file:/source/settings.gradle.kts')

        when:
        [true, false].each { topLevel ->
            context.apply(registry.scriptSource(Describables.of('script'), uri, target, topLevel)) {
                records.add(registry.currentAttribution())
            }
        }

        then:
        records*.origin*.kind == [DiagnosticOrigin.Kind.PROJECT_SCRIPT, DiagnosticOrigin.Kind.APPLIED_SCRIPT]
        records*.contributor*.kind == [ContributorKey.Kind.BUILD_AUTHOR, ContributorKey.Kind.APPLIED_SCRIPT]
    }

    def 'different project scripts share build author while preserving source scope and origin'() {
        given:
        def records = []

        when:
        [':one', ':two'].each { path ->
            def scriptTarget = ConfigurationTargetIdentifier.of(ProjectIdentity.forSubproject(Path.ROOT, Path.path(path)))
            context.apply(registry.scriptSource(Describables.of(path), new URI("file:/${path.substring(1)}/build.gradle"), scriptTarget, true)) {
                records.add(registry.currentAttribution())
            }
        }

        then:
        records[0].contributor == records[1].contributor
        records[0].origin != records[1].origin
        records*.sourceScope*.scopePath == [':one', ':two']
    }

    def 'deferred and nested callbacks retain registrant context and restore it on exceptions'() {
        given:
        def outer = registry.binarySource(Describables.of('outer'), 'Outer', 'outer', target)
        def inner = registry.binarySource(Describables.of('inner'), 'Inner', 'inner', target)
        def records = []
        def deferred
        def original = new RuntimeException('original')

        when:
        context.apply(outer) {
            deferred = context.reapplyCurrentLater {
                records.add(registry.currentAttribution())
                context.apply(inner) { records.add(registry.currentAttribution()) }
                records.add(registry.currentAttribution())
                throw original
            }
        }
        context.apply(inner) {
            try {
                deferred.execute(null)
            } catch (RuntimeException failure) {
                assert failure.is(original)
                records.add(registry.currentAttribution())
            }
        }

        then:
        records*.contributor*.identity == ['outer', 'inner', 'outer', 'inner']
        records[0].is(records[2])
        context.current() == null
    }

    def 'missing or unsupported context is unknown and disabled lookup fails without consulting context'() {
        expect:
        registry.currentAttribution().contributor.kind == ContributorKey.Kind.UNKNOWN

        when:
        def result
        context.apply(new UserCodeSource.Binary(Describables.of('not a scoped source'), 'SomePlugin', 'id')) {
            result = registry.currentAttribution()
        }

        then:
        result.contributor.kind == ContributorKey.Kind.UNKNOWN
        result.sourceScope.scopePath == 'unknown'

        when:
        new PropertyProvenanceRegistry(false, context).currentAttribution()

        then:
        thrown(IllegalStateException)
    }
}
