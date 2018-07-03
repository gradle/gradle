/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.changedetection.rules.AbstractTaskStateChangesTest
import org.gradle.api.internal.tasks.GenericFileNormalizer
import org.gradle.normalization.internal.InputNormalizationStrategy
import spock.lang.Issue

import static org.gradle.api.internal.changedetection.state.PathNormalizationStrategy.ABSOLUTE

class CacheBackedTaskHistoryRepositoryTest extends AbstractTaskStateChangesTest {
    static final NORMALIZATION_STRATEGY = InputNormalizationStrategy.NOT_CONFIGURED

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "adds context when input snapshot throws UncheckedIOException" () {
        setup:
        def cause = new UncheckedIOException("thrown from stub")
        def mockInputFileSnapshotter = Mock(FileCollectionSnapshotter)
        def mockInputFileSnapshotterRegistry = Mock(FileCollectionSnapshotterRegistry)

        when:
        CacheBackedTaskHistoryRepository.snapshotTaskFiles(stubTask, "Input", NORMALIZATION_STRATEGY, fileProperties(prop: "a"), mockInputFileSnapshotterRegistry)

        then:
        1 * mockInputFileSnapshotterRegistry.getSnapshotter(GenericFileNormalizer) >> mockInputFileSnapshotter
        1 * mockInputFileSnapshotter.snapshot(_, ABSOLUTE, NORMALIZATION_STRATEGY) >> { throw cause }
        0 * _

        def e = thrown(UncheckedIOException)
        e.message == "Failed to capture snapshot of input files for $stubTask property 'prop' during up-to-date check."
        e.cause == cause
    }
}
