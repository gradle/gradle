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

package org.gradle.api.internal.provider.provenance

import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.PropertyHost
import spock.lang.Specification

import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.RootKind.*
import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.Source
import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.SourceKnowledge.*
import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.SourceSelection.*
import static org.gradle.api.internal.provider.provenance.SemanticOperation.*

class SharedProvenanceContractTest extends Specification {
    private static final ScopeIdentity PROJECT = new ScopeIdentity('build-1', ':app')
    private static final TargetContext TARGET = new TargetContext(PROJECT, 'extension.message')
    private static final SemanticOperation MAP = update(Shape.MAP)
    private final MutationOccurrence c = occurrence(0, 'defaults', CONVENTION_BINDING)
    private final MutationOccurrence a = occurrence(1, 'A', MAP)
    private final MutationOccurrence s = occurrence(2, 'author', EXPLICIT_BINDING)
    private final MutationOccurrence b = occurrence(3, 'B', MAP)

    def 'SC-01 and SC-15 both policies project the same accepted facts at every checkpoint'() {
        given:
        def ordinary = new ProjectionPolicy(false)
        def collaborative = new ProjectionPolicy(true)

        when:
        [ordinary, collaborative].each { it.bindConvention(c) }

        then:
        reverseProjection(ordinary.view()) == [c]
        reverseProjection(collaborative.view()) == [c]
        collaborative.updates.size() == 0

        when:
        [ordinary, collaborative].each { it.acceptUpdate(a) }

        then:
        reverseProjection(ordinary.view()) == [a, c]
        reverseProjection(collaborative.view()) == [a, c]
        collaborative.updates.inApplicationOrder() == [a]
        ordinary.view().shadowedConfiguration.empty

        when:
        [ordinary, collaborative].each { it.bindExplicit(s) }

        then:
        reverseProjection(ordinary.view()) == [s]
        reverseProjection(collaborative.view()) == [a, s]
        collaborative.updates.inApplicationOrder() == [a]

        when:
        [ordinary, collaborative].each { it.acceptUpdate(b) }

        then:
        reverseProjection(ordinary.view()) == [b, s]
        reverseProjection(collaborative.view()) == [b, a, s]
        ordinary.view().shadowedConfiguration == [c]
        collaborative.view().shadowedConfiguration == [c]
        collaborative.updates.inApplicationOrder() == [a, b]
        // Ordering consumers see chronological positions, independently of the reverse report projection.
        collaborative.updates.inApplicationOrder()[0..1] == [a, b]
    }

    def 'SC-02 compound maps form one occurrence and repeated mutations share descriptors without collapsing'() {
        given:
        def compound = occurrence(1, 'A', update(Shape.MAP, Shape.MAP))
        def again = new MutationOccurrence(compound.scope, 2, compound.attribution, compound.operation)
        def prefix = UpdateSequence.empty().append(compound)
        def sequence = prefix.append(again)

        expect:
        compound.operation.shapes == [Shape.MAP, Shape.MAP]
        sequence.inApplicationOrder() == [compound, again]
        compound != again
        compound.attribution.is(again.attribution)
        prefix.inApplicationOrder() == [compound]
        sequence.reverseIterator().toList() == [again, compound]
    }

    def 'SC-03 provider creation and derivation are separate from the contributor binding the target'() {
        given:
        def calls = 0
        def upstream = new DefaultProvider<String>({ calls++; 'from A' })
        def derived = upstream.map { calls++; it.toUpperCase() }
        def target = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        def policy = new ProjectionPolicy(false)
        def binding = occurrence(0, 'B', EXPLICIT_BINDING)

        expect:
        policy.view().source.knowledge == UNCONFIGURED

        when:
        target.set(derived)
        policy.bindExplicit(binding)
        def view = policy.view()

        then:
        view.source.occurrence.attribution.contributor.identity == 'B'
        view.updates.size() == 0
        reverseProjection(view) == [binding]
        calls == 0

        when:
        def result = target.get()

        then:
        result == 'FROM A'
        calls == 2
        reverseProjection(view) == [binding]
        calls == 2
    }

    def 'SC-04 explicit missing selection never evaluates or falls back to the convention'() {
        given:
        def calls = 0
        def target = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        target.convention('default')
        def policy = new ProjectionPolicy(false)
        policy.bindConvention(c)

        when:
        target.set(new DefaultProvider<String>({ calls++; null }))
        policy.bindExplicit(s)
        def view = policy.view()

        then:
        view.source.selection == EXPLICIT
        view.source.occurrence == s
        view.shadowedConfiguration == [c]
        calls == 0

        when:
        def present = target.isPresent()

        then:
        !present
        calls == 1
        reverseProjection(view) == [s]
        calls == 1
    }

    def 'SC-04 unknown author unconfigured unattributed and lost provenance are distinct'() {
        given:
        def unknownAuthor = new Attribution(new ContributorKey('domain', ContributorKey.Kind.UNKNOWN, ''),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, '', 'unknown code'), PROJECT, null)
        def known = Source.known(EXPLICIT, new MutationOccurrence('target', 0, unknownAuthor, EXPLICIT_BINDING))

        expect:
        known.knowledge == KNOWN
        known.occurrence.attribution.contributor.kind == ContributorKey.Kind.UNKNOWN
        Source.unconfigured().knowledge == UNCONFIGURED
        Source.unattributed(EXPLICIT).knowledge == UNATTRIBUTED
        Source.unavailable(EXPLICIT, 'metadata lost').knowledge == UNAVAILABLE
        new EffectiveProvenanceView(TARGET, CAPTURED, Source.unavailable(EXPLICIT, 'metadata lost'),
            UpdateSequence.empty(), [], ['metadata lost']).completeLocal == false
    }

    def 'unclassified binding remains partial and cannot be interpreted as an update or authority'() {
        given:
        def opaque = occurrence(4, 'unknown', unclassifiedBinding('opaque previous-plan relationship'))
        def view = new EffectiveProvenanceView(TARGET, CAPTURED, Source.known(EXPLICIT, opaque),
            UpdateSequence.empty(), [], [opaque.operation.reason])

        expect:
        !view.completeLocal
        view.partialReasons == ['opaque previous-plan relationship']
        opaque.operation.kind == Kind.UNCLASSIFIED_BINDING

        when:
        UpdateSequence.empty().append(opaque)

        then:
        thrown(IllegalArgumentException)
    }

    def 'captured convention roots and live convention roots resolve differently at later checkpoints'() {
        given:
        def captured = new ProjectionPolicy(false)
        def live = new ProjectionPolicy(false, true)
        def replacement = occurrence(4, 'new defaults', CONVENTION_BINDING)
        [captured, live].each { it.bindConvention(c); it.acceptUpdate(a) }
        def oldLiveCheckpoint = live.view()

        when:
        [captured, live].each { it.bindConvention(replacement) }

        then:
        reverseProjection(captured.view()) == [a, c]
        captured.view().source.selection == CONVENTION
        captured.view().rootKind == CAPTURED
        captured.view().shadowedConfiguration == [replacement]
        reverseProjection(live.view()) == [a, replacement]
        live.view().rootKind == LIVE_CONVENTION
        live.view().shadowedConfiguration.empty
        reverseProjection(oldLiveCheckpoint) == [a, c]
    }

    def 'SC-14 domains and contributor kinds separate identities while applications do not'() {
        given:
        def same = new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'A')
        def otherDomain = new ContributorKey('other', ContributorKey.Kind.PLUGIN_ID, 'A')
        def classFallback = new ContributorKey('domain', ContributorKey.Kind.PLUGIN_CLASS, 'A')
        def application = new Attribution(a.attribution.contributor, a.attribution.origin, new ScopeIdentity('build-1', ':other'), 'application-2')
        def anotherMutation = new MutationOccurrence('other-target', a.sequence, application, MAP)

        expect:
        a.attribution.contributor == same
        same.hashCode() == a.attribution.contributor.hashCode()
        same != otherDomain
        same != classFallback
        application.contributor == a.attribution.contributor
        application != a.attribution
        anotherMutation != a
    }

    def 'settings origin and source scope stay separate from project target and script filename'() {
        given:
        def settings = new Attribution(new ContributorKey('domain', ContributorKey.Kind.SETTINGS, 'settings'),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.SETTINGS_SCRIPT, 'custom.gradle.kts', 'settings script'),
            new ScopeIdentity('build-1', 'settings'), null)
        def applied = new DiagnosticOrigin(DiagnosticOrigin.Kind.APPLIED_SCRIPT, 'settings.gradle.kts', 'applied script')

        expect:
        settings.sourceScope != TARGET.owner
        settings.origin.kind == DiagnosticOrigin.Kind.SETTINGS_SCRIPT
        applied.kind == DiagnosticOrigin.Kind.APPLIED_SCRIPT
    }

    def 'SC-14 descriptor-only round trip preserves copies and distinct repeated occurrences'() {
        given:
        def second = new MutationOccurrence(a.scope, 8, a.attribution, a.operation)
        def bytes = new ByteArrayOutputStream()
        def output = new DataOutputStream(bytes)
        [c, a, second, a].each { writeOccurrence(output, it) }
        def input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))

        when:
        def restored = (0..<4).collect { readOccurrence(input) }
        def sequence = UpdateSequence.empty().append(restored[1]).append(restored[2])
        def view = new EffectiveProvenanceView(TARGET, CAPTURED, Source.known(CONVENTION, restored[0]), sequence, [], [])

        then:
        restored == [c, a, second, a]
        !restored[1].is(a)
        restored[1].attribution == a.attribution
        restored[1].operation.shapes == a.operation.shapes
        restored[1] == restored[3]
        restored[1] != restored[2]
        reverseProjection(view) == [second, a, c]
        input.available() == 0
        descriptorGraphOnly(view)
    }

    def 'views and update shapes reject external mutation'() {
        given:
        def shapes = [Shape.MAP] as Shape[]
        def operation = update(shapes)
        shapes[0] = Shape.ZIP
        def shadowed = [c]
        def reasons = ['boundary']
        def view = new EffectiveProvenanceView(TARGET, CAPTURED, Source.known(EXPLICIT, s), UpdateSequence.empty(), shadowed, reasons)
        shadowed.clear()
        reasons.clear()

        expect:
        operation.shapes == [Shape.MAP]
        view.shadowedConfiguration == [c]
        view.partialReasons == ['boundary']

        when:
        view.shadowedConfiguration.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def 'descriptor-only checkpoint round trip retains target source coverage and occurrence relationships'() {
        given:
        def policy = new ProjectionPolicy(true)
        policy.bindConvention(c)
        policy.acceptUpdate(a)
        policy.bindExplicit(s)
        policy.acceptUpdate(b)
        def original = policy.view()
        def bytes = new ByteArrayOutputStream()
        writeView(new DataOutputStream(bytes), original)

        when:
        def restored = readView(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))

        then:
        restored.target == original.target
        !restored.target.is(original.target)
        restored.rootKind == original.rootKind
        restored.source.selection == EXPLICIT
        reverseProjection(restored) == [b, a, s]
        restored.shadowedConfiguration == [c]
        restored.completeLocal
        descriptorGraphOnly(restored)
    }

    def 'descriptor round trip preserves every semantic operation and absent application detail'() {
        given:
        def attribution = new Attribution(a.attribution.contributor, a.attribution.origin, PROJECT, null)
        def original = new MutationOccurrence('copy-scope', 5, attribution, operation)
        def bytes = new ByteArrayOutputStream()
        writeOccurrence(new DataOutputStream(bytes), original)

        when:
        def restored = readOccurrence(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))

        then:
        restored == original
        restored.attribution == attribution
        restored.operation.kind == operation.kind
        restored.operation.shapes == operation.shapes
        restored.operation.reason == operation.reason
        descriptorGraphOnly(restored)

        where:
        operation << [EXPLICIT_BINDING, CONVENTION_BINDING, CLEAR_EXPLICIT, CLEAR_CONVENTION, PROMOTE_CONVENTION,
                      unclassifiedBinding('opaque'), update(Shape.MAP, Shape.FLAT_MAP, Shape.ZIP, Shape.APPEND, Shape.REMOVE)]
    }

    def 'a root occurrence cannot also appear as shadowed even through a descriptor copy'() {
        when:
        new EffectiveProvenanceView(TARGET, CAPTURED, Source.known(CONVENTION, c), UpdateSequence.empty(),
            [new MutationOccurrence(c.scope, c.sequence, c.attribution, c.operation)], [])

        then:
        thrown(IllegalArgumentException)
    }

    def 'sequence growth is unbounded by diagnostic limits and snapshots remain unchanged at #count updates'() {
        given:
        def sequence = UpdateSequence.empty()
        def snapshot = sequence

        when:
        count.times { sequence = sequence.append(new MutationOccurrence('target', it, a.attribution, MAP)) }

        then:
        snapshot.size() == 0
        sequence.size() == count
        sequence.inApplicationOrder()*.sequence == (0L..<count).toList()
        sequence.reverseIterator().toList()*.sequence == (0L..<count).toList().reverse()

        where:
        count << [0, 1, 8, 128, 4096]
    }

    private static MutationOccurrence occurrence(long id, String contributor, SemanticOperation operation) {
        def keyKind = contributor == 'author' ? ContributorKey.Kind.BUILD_AUTHOR : ContributorKey.Kind.PLUGIN_ID
        def originKind = contributor == 'author' ? DiagnosticOrigin.Kind.PROJECT_SCRIPT : DiagnosticOrigin.Kind.PLUGIN_ID
        new MutationOccurrence('target', id, new Attribution(new ContributorKey('domain', keyKind, contributor),
            new DiagnosticOrigin(originKind, contributor, contributor), PROJECT, 'application-1'), operation)
    }

    private static List<MutationOccurrence> reverseProjection(EffectiveProvenanceView view) {
        def result = view.updates.reverseIterator().toList()
        if (view.source.occurrence != null) {
            result.add(view.source.occurrence)
        }
        result
    }

    // Metadata transition policy only. There is no evaluator, runtime authorization or ordering implementation.
    private static class ProjectionPolicy {
        final boolean preserveUpdatesOnRebind
        final boolean liveConvention
        MutationOccurrence convention
        Source root
        UpdateSequence updates = UpdateSequence.empty()

        ProjectionPolicy(boolean preserveUpdatesOnRebind, boolean liveConvention = false) {
            this.preserveUpdatesOnRebind = preserveUpdatesOnRebind
            this.liveConvention = liveConvention
        }

        void bindConvention(MutationOccurrence occurrence) {
            convention = occurrence
        }

        void bindExplicit(MutationOccurrence occurrence) {
            root = Source.known(EXPLICIT, occurrence)
            if (!preserveUpdatesOnRebind) {
                updates = UpdateSequence.empty()
            }
        }

        void acceptUpdate(MutationOccurrence occurrence) {
            if (root == null && !liveConvention && !preserveUpdatesOnRebind) {
                root = selectedSource()
            }
            updates = updates.append(occurrence)
        }

        Source selectedSource() {
            root ?: (convention == null ? Source.unconfigured() : Source.known(CONVENTION, convention))
        }

        EffectiveProvenanceView view() {
            def selected = selectedSource()
            new EffectiveProvenanceView(TARGET, root == null ? LIVE_CONVENTION : CAPTURED, selected, updates,
                convention != null && convention != selected.occurrence ? [convention] : [], [])
        }
    }

    // A deliberately test-only descriptor wire adapter. Production transport belongs to D4, not S1.
    private static void writeView(DataOutputStream out, EffectiveProvenanceView view) {
        [view.target.owner.buildIdentity, view.target.owner.scopePath, view.target.modelPath,
         view.rootKind.name(), view.source.selection.name(), view.source.knowledge.name(), view.source.reason].each { out.writeUTF(it) }
        if (view.source.knowledge == KNOWN) {
            writeOccurrence(out, view.source.occurrence)
        }
        out.writeInt(view.updates.size())
        view.updates.inApplicationOrder().each { writeOccurrence(out, it) }
        out.writeInt(view.shadowedConfiguration.size())
        view.shadowedConfiguration.each { writeOccurrence(out, it) }
        out.writeInt(view.partialReasons.size())
        view.partialReasons.each { out.writeUTF(it) }
    }

    private static EffectiveProvenanceView readView(DataInputStream input) {
        def target = new TargetContext(new ScopeIdentity(input.readUTF(), input.readUTF()), input.readUTF())
        def rootKind = EffectiveProvenanceView.RootKind.valueOf(input.readUTF())
        def selection = EffectiveProvenanceView.SourceSelection.valueOf(input.readUTF())
        def knowledge = EffectiveProvenanceView.SourceKnowledge.valueOf(input.readUTF())
        def reason = input.readUTF()
        def selected = knowledge == KNOWN ? Source.known(selection, readOccurrence(input)) :
            knowledge == UNCONFIGURED ? Source.unconfigured() :
                knowledge == UNATTRIBUTED ? Source.unattributed(selection) : Source.unavailable(selection, reason)
        def updates = UpdateSequence.empty()
        input.readInt().times { updates = updates.append(readOccurrence(input)) }
        def shadowed = (0..<input.readInt()).collect { readOccurrence(input) }
        def reasons = (0..<input.readInt()).collect { input.readUTF() }
        new EffectiveProvenanceView(target, rootKind, selected, updates, shadowed, reasons)
    }

    private static void writeOccurrence(DataOutputStream out, MutationOccurrence occurrence) {
        out.writeUTF(occurrence.scope)
        out.writeLong(occurrence.sequence)
        def attribution = occurrence.attribution
        [attribution.contributor.domain, attribution.contributor.kind.name(), attribution.contributor.identity,
         attribution.origin.kind.name(), attribution.origin.identifier, attribution.origin.displayName,
         attribution.sourceScope.buildIdentity, attribution.sourceScope.scopePath].each { out.writeUTF(it) }
        out.writeBoolean(attribution.applicationToken != null)
        if (attribution.applicationToken != null) {
            out.writeUTF(attribution.applicationToken)
        }
        out.writeUTF(occurrence.operation.kind.name())
        out.writeUTF(occurrence.operation.reason)
        out.writeInt(occurrence.operation.shapes.size())
        occurrence.operation.shapes.each { out.writeUTF(it.name()) }
    }

    private static MutationOccurrence readOccurrence(DataInputStream input) {
        def scope = input.readUTF()
        def id = input.readLong()
        def key = new ContributorKey(input.readUTF(), ContributorKey.Kind.valueOf(input.readUTF()), input.readUTF())
        def origin = new DiagnosticOrigin(DiagnosticOrigin.Kind.valueOf(input.readUTF()), input.readUTF(), input.readUTF())
        def source = new ScopeIdentity(input.readUTF(), input.readUTF())
        def attribution = new Attribution(key, origin, source, input.readBoolean() ? input.readUTF() : null)
        def kind = Kind.valueOf(input.readUTF())
        def reason = input.readUTF()
        def shapes = (0..<input.readInt()).collect { Shape.valueOf(input.readUTF()) } as Shape[]
        def operation = kind == Kind.UPDATE ? update(shapes) : kind == Kind.UNCLASSIFIED_BINDING ? unclassifiedBinding(reason) :
            [EXPLICIT_BINDING, CONVENTION_BINDING, CLEAR_EXPLICIT, CLEAR_CONVENTION, PROMOTE_CONVENTION].find { it.kind == kind }
        new MutationOccurrence(scope, id, attribution, operation)
    }

    private static boolean descriptorGraphOnly(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Enum) {
            return true
        }
        if (value instanceof Collection) {
            return value.every { descriptorGraphOnly(it) }
        }
        assert value.class.package.name == 'org.gradle.api.internal.provider.provenance'
        value.class.declaredFields.findAll { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.every {
            it.accessible = true
            descriptorGraphOnly(it.get(value))
        }
    }
}
