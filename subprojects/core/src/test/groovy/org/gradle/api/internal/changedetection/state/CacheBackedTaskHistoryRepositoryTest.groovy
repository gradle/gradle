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
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer
import org.gradle.normalization.internal.InputNormalizationStrategy
import spock.lang.Issue

class CacheBackedTaskHistoryRepositoryTest extends AbstractTaskStateChangesTest {
    static final NORMALIZATION_STRATEGY = InputNormalizationStrategy.NO_NORMALIZATION

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "adds context when input snapshot throws UncheckedIOException" () {
        setup:
        def cause = new UncheckedIOException("thrown from stub")
        def mockInputFileFingerprinter = Mock(FileCollectionFingerprinter)
        def mockInputFileFingerprinterRegistry = Mock(FileCollectionFingerprinterRegistry)

        when:
        CacheBackedTaskHistoryRepository.fingerprintTaskFiles(stubTask, "Input", NORMALIZATION_STRATEGY, fileProperties(prop: "a"), mockInputFileFingerprinterRegistry)

        then:
        1 * mockInputFileFingerprinterRegistry.getFingerprinter(AbsolutePathInputNormalizer) >> mockInputFileFingerprinter
        1 * mockInputFileFingerprinter.fingerprint(_, NORMALIZATION_STRATEGY) >> { throw cause }
        0 * _

        def e = thrown(UncheckedIOException)
        e.message == "Failed to capture fingerprint of input files for $stubTask property 'prop' during up-to-date check."
        e.cause == cause
    }
}
