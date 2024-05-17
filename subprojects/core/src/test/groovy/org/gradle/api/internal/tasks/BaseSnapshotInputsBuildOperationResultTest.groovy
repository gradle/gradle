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

import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.execution.model.OutputNormalizer
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.snapshot.TestSnapshotFixture
import spock.lang.Specification

import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_ABSOLUTE_PATH
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_CLASSPATH
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_IGNORED_PATH
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_NAME_ONLY
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FINGERPRINTING_STRATEGY_RELATIVE_PATH
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.from
import static org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.fromNormalizer

class BaseSnapshotInputsBuildOperationResultTest extends Specification implements TestSnapshotFixture {

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

    def "can convert normalizer class into a PropertyAttribute"(FileNormalizer normalizer, BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute expectedPropertyAttribute) {
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
        thrown(IllegalStateException)
    }

}
