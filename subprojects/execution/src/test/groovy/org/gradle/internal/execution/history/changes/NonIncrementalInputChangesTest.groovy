/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification

class NonIncrementalInputChangesTest extends Specification {

    def "can iterate changes more than once"() {
        def fingerprint = DefaultCurrentFileCollectionFingerprint.from([new RegularFileSnapshot("/some/where", "where", HashCode.fromInt(1234), 0)], AbsolutePathFingerprintingStrategy.INCLUDE_MISSING)

        def changes = new NonIncrementalInputChanges(ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of("input", fingerprint), ImmutableMultimap.of("value", "input"))
        def expectedChangedFiles = [new File("/some/where")]

        when:
        def allFileChanges = changes.allFileChanges
        def fileChanges = changes.getFileChanges("value")

        then:
        allFileChanges*.file == expectedChangedFiles
        allFileChanges*.file == expectedChangedFiles

        fileChanges*.file == expectedChangedFiles
        fileChanges*.file == expectedChangedFiles
    }

}
