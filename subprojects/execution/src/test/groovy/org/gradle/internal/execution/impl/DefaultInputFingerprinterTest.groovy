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

package org.gradle.internal.execution.impl

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.file.FileCollection
import org.gradle.internal.execution.FileCollectionFingerprinter
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.execution.FileNormalizationSpec
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputFingerprinter.Result
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter
import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL

class DefaultInputFingerprinterTest extends Specification {
    def fingerprinter = Mock(FileCollectionFingerprinter)
    def snapshotter = Mock(FileCollectionSnapshotter)
    def fingerprinterRegistry = Stub(FileCollectionFingerprinterRegistry) {
        getFingerprinter(_ as FileNormalizationSpec) >> fingerprinter
    }
    def valueSnapshotter = Mock(ValueSnapshotter)
    def inputFingerprinter = new DefaultInputFingerprinter(snapshotter, fingerprinterRegistry, valueSnapshotter)

    def input = Mock(Object)
    def inputSnapshot = Mock(ValueSnapshot)
    def fileInput = Mock(FileCollection)
    def fileInputSnapshot = Mock(FileSystemSnapshot)
    def fileInputSnapshotResult = Mock(FileCollectionSnapshotter.Result)
    def fileInputFingerprint = Mock(CurrentFileCollectionFingerprint)
    def normalizer = Mock(FileNormalizer)

    def "visits properties"() {
        when:
        def result = fingerprintInputProperties { visitor ->
            visitor.visitInputProperty("input") { input }
            visitor.visitInputFileProperty(
                "file",
                NON_INCREMENTAL,
                new InputFileValueSupplier(fileInput, normalizer, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, { fileInput }))
        }

        then:
        1 * valueSnapshotter.snapshot(input) >> inputSnapshot
        1 * snapshotter.snapshot(fileInput) >> fileInputSnapshotResult
        _ * fileInputSnapshotResult.containsArchiveTrees() >> false
        1 * fileInputSnapshotResult.snapshot >> fileInputSnapshot
        1 * fingerprinter.fingerprint(fileInputSnapshot, null) >> fileInputFingerprint
        0 * _

        then:
        result.valueSnapshots as Map == ["input": inputSnapshot]
        result.fileFingerprints as Map == ["file": fileInputFingerprint]
    }

    def "marks archive trees as properties requiring empty check"() {
        def archiveTreeInput = Mock(FileCollection)
        def archiveTreeInputSnapshotResult = Mock(FileCollectionSnapshotter.Result)
        def archiveTreeInputSnapshot = Mock(FileSystemSnapshot)
        def archiveTreeInputFingerprint = Mock(CurrentFileCollectionFingerprint)

        when:
        def result = fingerprintInputProperties { visitor ->
            visitor.visitInputFileProperty(
                "file",
                NON_INCREMENTAL,
                new InputFileValueSupplier(fileInput, normalizer, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, { fileInput }))
            visitor.visitInputFileProperty(
                "archiveTree",
                NON_INCREMENTAL,
                new InputFileValueSupplier(fileInput, normalizer, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, { archiveTreeInput }))
        }

        then:
        1 * snapshotter.snapshot(fileInput) >> fileInputSnapshotResult
        _ * fileInputSnapshotResult.fileTreeOnly >> false
        _ * fileInputSnapshotResult.containsArchiveTrees() >> false
        1 * fileInputSnapshotResult.snapshot >> fileInputSnapshot
        1 * fingerprinter.fingerprint(fileInputSnapshot, null) >> fileInputFingerprint

        then:
        1 * snapshotter.snapshot(archiveTreeInput) >> archiveTreeInputSnapshotResult
        _ * archiveTreeInputSnapshotResult.fileTreeOnly >> false
        _ * archiveTreeInputSnapshotResult.containsArchiveTrees() >> true
        1 * archiveTreeInputSnapshotResult.snapshot >> archiveTreeInputSnapshot
        1 * fingerprinter.fingerprint(archiveTreeInputSnapshot, null) >> archiveTreeInputFingerprint

        0 * _

        then:
        result.fileFingerprints as Map == [
            "file": fileInputFingerprint,
            "archiveTree": archiveTreeInputFingerprint
        ]
        result.propertiesRequiringIsEmptyCheck == (["archiveTree"] as Set)
    }

    def "ignores already known properties"() {
        when:
        def result = fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("input", inputSnapshot),
            ImmutableSortedMap.of("file", fileInputFingerprint)
        ) { visitor ->
            visitor.visitInputProperty("input") { throw new RuntimeException("Shouldn't evaluate this") }
            visitor.visitInputFileProperty(
                "file",
                NON_INCREMENTAL,
                new InputFileValueSupplier(fileInput, normalizer, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, { throw new RuntimeException("Shouldn't evaluate this") }))
        }

        then:
        0 * _

        then:
        result.valueSnapshots as Map == [:]
        result.fileFingerprints as Map == [:]
    }

    def "reuses previous input snapshots when visiting input properties"() {
        def previousSnapshot = Mock(ValueSnapshot)

        when:
        def result = fingerprintInputProperties(ImmutableSortedMap.of("identity", previousSnapshot)) { visitor ->
            visitor.visitInputProperty("identity") { input }
        }

        then:
        1 * valueSnapshotter.snapshot(input, previousSnapshot) >> inputSnapshot
        0 * _

        then:
        result.valueSnapshots as Map == ["identity": inputSnapshot]
        result.fileFingerprints as Map == [:]
    }

    def "reports value snapshotting problem"() {
        def failure = new RuntimeException("Error")
        def input = "failing-value"

        when:
        fingerprintInputProperties { visitor ->
            visitor.visitInputProperty("input") { input }
        }

        then:
        1 * valueSnapshotter.snapshot(input) >> { throw failure }
        0 * _

        then:
        def ex = thrown InputFingerprinter.InputFingerprintingException
        ex.message == "Cannot fingerprint input property 'input': value 'failing-value' cannot be serialized."
        ex.propertyName == "input"
        ex.cause == failure
    }

    def "reports file snapshotting problem"() {
        def failure = new UncheckedIOException(new IOException("Error"))
        when:
        fingerprintInputProperties { visitor ->
            visitor.visitInputFileProperty(
                "file",
                NON_INCREMENTAL,
                new InputFileValueSupplier(fileInput, normalizer, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, { fileInput }))
        }

        then:
        1 * snapshotter.snapshot(fileInput) >> { throw failure }
        0 * _

        then:
        def ex = thrown InputFingerprinter.InputFileFingerprintingException
        ex.message == "Cannot fingerprint input file property 'file': java.io.IOException: Error"
        ex.propertyName == "file"
        ex.cause == failure
    }

    private Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots = ImmutableSortedMap.of(),
        ImmutableSortedMap<String, FileCollectionFingerprint> previousFingerprints = ImmutableSortedMap.of(),
        ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots = ImmutableSortedMap.of(),
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints = ImmutableSortedMap.of(),
        Consumer<InputVisitor> inputs
    ) {
        inputFingerprinter.fingerprintInputProperties(previousValueSnapshots, previousFingerprints, knownCurrentValueSnapshots, knownCurrentFingerprints, inputs)
    }
}
