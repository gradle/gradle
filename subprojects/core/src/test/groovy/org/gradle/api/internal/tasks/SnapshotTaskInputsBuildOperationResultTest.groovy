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

package org.gradle.api.internal.tasks

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec
import org.gradle.caching.BuildCacheKey
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.execution.model.OutputNormalizer
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification

import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_ABSOLUTE_PATH
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_CLASSPATH
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_IGNORED_PATH
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_NAME_ONLY
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_RELATIVE_PATH
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.from
import static org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.fromNormalizer
import static org.gradle.internal.fingerprint.DirectorySensitivity.DEFAULT
import static org.gradle.internal.fingerprint.DirectorySensitivity.IGNORE_DIRECTORIES
import static org.gradle.internal.fingerprint.LineEndingSensitivity.NORMALIZE_LINE_ENDINGS

class SnapshotTaskInputsBuildOperationResultTest extends Specification implements TestSnapshotFixture {

    def "can convert line ending sensitivity into a PropertyAttribute"(LineEndingSensitivity lineEndingSensitivity) {
        expect:
        from(lineEndingSensitivity)

        where:
        lineEndingSensitivity << LineEndingSensitivity.values()
    }

    def "can convert directory sensitivity into a PropertyAttribute"(DirectorySensitivity directorySensitivity) {
        expect:
        from(directorySensitivity)

        where:
        directorySensitivity << DirectorySensitivity.values()
    }

    def "can convert normalizer class into a PropertyAttribute"(FileNormalizer normalizer, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute expectedPropertyAttribute) {
        expect:
        fromNormalizer(normalizer) == expectedPropertyAttribute

        where:
        normalizer                       | expectedPropertyAttribute
        InputNormalizer.RUNTIME_CLASSPATH | FINGERPRINTING_STRATEGY_CLASSPATH
        InputNormalizer.COMPILE_CLASSPATH | FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH
        InputNormalizer.ABSOLUTE_PATH     | FINGERPRINTING_STRATEGY_ABSOLUTE_PATH
        InputNormalizer.RELATIVE_PATH     | FINGERPRINTING_STRATEGY_RELATIVE_PATH
        InputNormalizer.NAME_ONLY         | FINGERPRINTING_STRATEGY_NAME_ONLY
        InputNormalizer.IGNORE_PATH       | FINGERPRINTING_STRATEGY_IGNORED_PATH
    }

    def "throws when converting an unsupported normalizer class into a PropertyAttribute"() {
        when:
        fromNormalizer(OutputNormalizer.INSTANCE)

        then:
        def t = thrown(IllegalStateException)
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "properly visits structure when ignoring directories"() {
        given:
        def visitor = Mock(SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor)
        def inputFileProperty = Mock(InputFilePropertySpec) {
            getDirectorySensitivity() >> IGNORE_DIRECTORIES
            getLineEndingNormalization() >> NORMALIZE_LINE_ENDINGS
            getNormalizer() >> InputNormalizer.ABSOLUTE_PATH
            getPropertyName() >> 'foo'
        }
        def snapshots = directory('/foo', [
            regularFile('/foo/one.txt'),
            directory('/foo/empty', [
                directory('/foo/empty/empty', [])
            ]),
            directory('/foo/sub', [
                regularFile('/foo/sub/two.txt')
            ])
        ])
        def beforeExecutionState = Mock(BeforeExecutionState) {
            getInputFileProperties() >> ImmutableSortedMap.of('foo',
                Mock(CurrentFileCollectionFingerprint) {
                    getHash() >> TestHashCodes.hashCodeFrom(345)
                    getFingerprints() >> [
                        '/foo/one.txt': new DefaultFileSystemLocationFingerprint('/foo/one.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(123)),
                        '/foo/sub/two.txt': new DefaultFileSystemLocationFingerprint('/foo/sub/two.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(234)),
                    ]
                    getSnapshot() >> snapshots
                }
            )
        }
        def cachingState = CachingState.enabled(Mock(BuildCacheKey), beforeExecutionState)
        def buildOpResult = new SnapshotTaskInputsBuildOperationResult(
            cachingState,
            [inputFileProperty] as Set
        )

        when:
        buildOpResult.visitInputFileProperties(visitor)

        then:
        1 * visitor.preProperty(_)
        1 * visitor.preRoot(_)

        and:
        1 * visitor.preDirectory { it.path == '/foo' }

        and:
        1 * visitor.file() { it.path == '/foo/one.txt' }

        and:
        1 * visitor.preDirectory { it.path == '/foo/sub' }

        and:
        1 * visitor.file() { it.path == '/foo/sub/two.txt' }

        and:
        2 * visitor.postDirectory()
        1 * visitor.postRoot()
        1 * visitor.postProperty()

        and:
        0 * visitor._
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "properly visits structure when ignoring only the root directory"() {
        given:
        def visitor = Mock(SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor)
        def inputFileProperty = Mock(InputFilePropertySpec) {
            getDirectorySensitivity() >> DEFAULT
            getLineEndingNormalization() >> NORMALIZE_LINE_ENDINGS
            getNormalizer() >> InputNormalizer.RELATIVE_PATH
            getPropertyName() >> 'inputFiles'
        }
        def snapshots = directory('/input', [
            directory('/input/foo', []),
        ])
        def beforeExecutionState = Mock(BeforeExecutionState) {
            getInputFileProperties() >> ImmutableSortedMap.of('inputFiles',
                Mock(CurrentFileCollectionFingerprint) {
                    getHash() >> TestHashCodes.hashCodeFrom(345)
                    getFingerprints() >> [
                        '/input/foo': new DefaultFileSystemLocationFingerprint('/input/foo', FileType.Directory, TestHashCodes.hashCodeFrom(123))
                    ]
                    getSnapshot() >> snapshots
                }
            )
        }
        def cachingState = CachingState.enabled(Mock(BuildCacheKey), beforeExecutionState)
        def buildOpResult = new SnapshotTaskInputsBuildOperationResult(
            cachingState,
            [inputFileProperty] as Set
        )

        when:
        buildOpResult.visitInputFileProperties(visitor)

        then:
        1 * visitor.preProperty(_)
        1 * visitor.preRoot({ it.path == '/input' })

        and:
        1 * visitor.preDirectory { it.path == '/input' }

        and:
        1 * visitor.preDirectory() { it.path == '/input/foo' }

        and:
        2 * visitor.postDirectory()
        1 * visitor.postRoot()
        1 * visitor.postProperty()

        and:
        0 * visitor._
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "properly visits structure when not ignoring directories"() {
        given:
        def visitor = Mock(SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor)
        def inputFileProperty = Mock(InputFilePropertySpec) {
            getDirectorySensitivity() >> DEFAULT
            getLineEndingNormalization() >> NORMALIZE_LINE_ENDINGS
            getNormalizer() >> InputNormalizer.ABSOLUTE_PATH
            getPropertyName() >> 'foo'
        }
        def snapshots = directory('/foo', [
            regularFile('/foo/one.txt'),
            directory('/foo/empty', [
                directory('/foo/empty/empty', [])
            ]),
            directory('/foo/sub', [
                regularFile('/foo/sub/two.txt')
            ])
        ])
        def beforeExecutionState = Mock(BeforeExecutionState) {
            getInputFileProperties() >> ImmutableSortedMap.of('foo',
                Mock(CurrentFileCollectionFingerprint) {
                    getHash() >> TestHashCodes.hashCodeFrom(345)
                    getFingerprints() >> [
                        '/foo/one.txt': new DefaultFileSystemLocationFingerprint('/foo/one.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(123)),
                        '/foo/sub/two.txt': new DefaultFileSystemLocationFingerprint('/foo/sub/two.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(234)),
                        '/foo': new DefaultFileSystemLocationFingerprint('/foo', FileType.Directory, TestHashCodes.hashCodeFrom(123)),
                        '/foo/empty': new DefaultFileSystemLocationFingerprint('/foo/empty', FileType.Directory, TestHashCodes.hashCodeFrom(123)),
                        '/foo/empty/empty': new DefaultFileSystemLocationFingerprint('/foo/empty/empty', FileType.Directory, TestHashCodes.hashCodeFrom(123)),
                        '/foo/sub': new DefaultFileSystemLocationFingerprint('/foo/sub', FileType.Directory, TestHashCodes.hashCodeFrom(123)),
                    ]
                    getSnapshot() >> snapshots
                }
            )
        }
        def cachingState = CachingState.enabled(Mock(BuildCacheKey), beforeExecutionState)
        def buildOpResult = new SnapshotTaskInputsBuildOperationResult(
            cachingState,
            [inputFileProperty] as Set
        )

        when:
        buildOpResult.visitInputFileProperties(visitor)

        then:
        1 * visitor.preProperty(_)
        1 * visitor.preRoot(_)

        and:
        1 * visitor.preDirectory { it.path == '/foo' }

        and:
        1 * visitor.file() { it.path == '/foo/one.txt' }

        and:
        1 * visitor.preDirectory { it.path == '/foo/empty' }

        and:
        1 * visitor.preDirectory { it.path == '/foo/empty/empty' }

        and:
        1 * visitor.postDirectory()

        and:
        1 * visitor.postDirectory()

        and:
        1 * visitor.preDirectory { it.path == '/foo/sub' }

        and:
        1 * visitor.file() { it.path == '/foo/sub/two.txt' }

        and:
        2 * visitor.postDirectory()
        1 * visitor.postRoot()
        1 * visitor.postProperty()

        and:
        0 * visitor._
    }

}
