/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.fingerprint.impl

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.FileNormalizer
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.FileValueSupplier
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinter
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.execution.fingerprint.InputFingerprinter.Result
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter
import spock.lang.Specification

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.NON_IDENTITY
import static org.gradle.internal.execution.UnitOfWork.InputPropertyType.NON_INCREMENTAL

class DefaultInputFingerprinterTest extends Specification {
    def work = Mock(UnitOfWork)
    def fingerprinter = Mock(FileCollectionFingerprinter)
    def fingerprinterRegistry = Stub(FileCollectionFingerprinterRegistry) {
        getFingerprinter(_ as FileNormalizationSpec) >> fingerprinter
    }
    def valueSnapshotter = Mock(ValueSnapshotter)
    def inputFingerprinter = new DefaultInputFingerprinter(fingerprinterRegistry, valueSnapshotter)

    def identityInput = Mock(Object)
    def identityInputSnapshot = Mock(ValueSnapshot)
    def nonIdentityInput = Mock(Object)
    def nonIdentityInputSnapshot = Mock(ValueSnapshot)
    def identityInputFiles = Mock(FileCollection)
    def nonIdentityInputFiles = Mock(FileCollection)
    def identityFileInputFingerprint = Mock(CurrentFileCollectionFingerprint)
    def nonIdentityFileInputFingerprint = Mock(CurrentFileCollectionFingerprint)

    def "visits properties"() {
        when:
        def result = fingerprintInputProperties()

        then:
        1 * work.visitInputs(_) >> { UnitOfWork.InputVisitor visitor ->
            visitor.visitInputProperty("identity", UnitOfWork.IdentityKind.IDENTITY) { identityInput }
            visitor.visitInputProperty("non-identity", NON_IDENTITY) { nonIdentityInput }
            visitor.visitInputFileProperty(
                "identity-file",
                NON_INCREMENTAL,
                UnitOfWork.IdentityKind.IDENTITY,
                new FileValueSupplier(identityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { identityInputFiles }))
            visitor.visitInputFileProperty(
                "non-identity-file",
                NON_INCREMENTAL,
                NON_IDENTITY,
                new FileValueSupplier(nonIdentityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { nonIdentityInputFiles }))
        }
        1 * valueSnapshotter.snapshot(identityInput) >> identityInputSnapshot
        1 * valueSnapshotter.snapshot(nonIdentityInput) >> nonIdentityInputSnapshot
        1 * fingerprinter.fingerprint(identityInputFiles) >> identityFileInputFingerprint
        1 * fingerprinter.fingerprint(nonIdentityInputFiles) >> nonIdentityFileInputFingerprint
        0 * _

        then:
        result.valueSnapshots as Map == ["non-identity": nonIdentityInputSnapshot, "identity" : identityInputSnapshot]
        result.fileFingerprints as Map == ["non-identity-file": nonIdentityFileInputFingerprint, "identity-file": identityFileInputFingerprint]
    }

    def "ignores already known properties"() {
        when:
        def result = fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("non-identity", nonIdentityInputSnapshot),
            ImmutableSortedMap.of("non-identity-file", nonIdentityFileInputFingerprint)
        )

        then:
        1 * work.visitInputs(_) >> { UnitOfWork.InputVisitor visitor ->
            visitor.visitInputProperty("identity", UnitOfWork.IdentityKind.IDENTITY) { identityInput }
            visitor.visitInputProperty("non-identity", NON_IDENTITY) { throw new RuntimeException("Shouldn't evaluate this") }
            visitor.visitInputFileProperty(
                "identity-file",
                NON_INCREMENTAL,
                UnitOfWork.IdentityKind.IDENTITY,
                new FileValueSupplier(identityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { identityInputFiles }))
            visitor.visitInputFileProperty(
                "non-identity-file",
                NON_INCREMENTAL,
                NON_IDENTITY,
                new FileValueSupplier(nonIdentityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { throw new RuntimeException("Shouldn't evaluate this") }))
        }
        1 * valueSnapshotter.snapshot(identityInput) >> identityInputSnapshot
        1 * fingerprinter.fingerprint(identityInputFiles) >> identityFileInputFingerprint
        0 * _

        then:
        result.valueSnapshots as Map == ["identity" : identityInputSnapshot]
        result.fileFingerprints as Map == ["identity-file": identityFileInputFingerprint]
    }

    def "filters the right properties"() {
        when:
        def result = fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            { propertyName, type, identity -> identity == UnitOfWork.IdentityKind.IDENTITY }
        )

        then:
        1 * work.visitInputs(_) >> { UnitOfWork.InputVisitor visitor ->
            visitor.visitInputProperty("identity", UnitOfWork.IdentityKind.IDENTITY) { identityInput }
            visitor.visitInputProperty("non-identity", NON_IDENTITY) { throw new RuntimeException("Shouldn't evaluate this") }
            visitor.visitInputFileProperty(
                "identity-file",
                NON_INCREMENTAL,
                UnitOfWork.IdentityKind.IDENTITY,
                new FileValueSupplier(identityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { identityInputFiles }))
            visitor.visitInputFileProperty(
                "non-identity-file",
                NON_INCREMENTAL,
                NON_IDENTITY,
                new FileValueSupplier(nonIdentityInputFiles, FileNormalizer, DirectorySensitivity.DEFAULT, { throw new RuntimeException("Shouldn't evaluate this") }))
        }
        1 * valueSnapshotter.snapshot(identityInput) >> identityInputSnapshot
        1 * fingerprinter.fingerprint(identityInputFiles) >> identityFileInputFingerprint
        0 * _

        then:
        result.valueSnapshots as Map == ["identity" : identityInputSnapshot]
        result.fileFingerprints as Map == ["identity-file": identityFileInputFingerprint]
    }

    def "reuses previous input snapshots when visiting input properties"() {
        def previousSnapshot = Mock(ValueSnapshot)

        when:
        def result = fingerprintInputProperties(ImmutableSortedMap.of("identity", previousSnapshot))

        then:
        1 * work.visitInputs(_) >> { UnitOfWork.InputVisitor visitor ->
            visitor.visitInputProperty("identity", UnitOfWork.IdentityKind.IDENTITY) { identityInput }
        }
        1 * valueSnapshotter.snapshot(identityInput, previousSnapshot) >> identityInputSnapshot
        0 * _

        then:
        result.valueSnapshots as Map == ["identity": identityInputSnapshot]
        result.fileFingerprints as Map == [:]
    }

    private Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots = ImmutableSortedMap.of(),
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots = ImmutableSortedMap.of(),
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints = ImmutableSortedMap.of(),
        InputFingerprinter.InputPropertyPredicate filter = { propertyName, type, identity -> true }
    ) {
        inputFingerprinter.fingerprintInputProperties(work, previousValueSnapshots, knownValueSnapshots, knownFingerprints, filter)
    }
}
