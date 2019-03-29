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

package org.gradle.api.internal.tasks

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.ImmutableSortedSet
import org.gradle.internal.execution.caching.CachingInputs
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import spock.lang.Specification

class SnapshotTaskInputsBuildOperationResultTest extends Specification {

    def "adapts key to result interface"() {
        given:
        def inputs = Mock(CachingInputs)
        def cachingState = Mock(CachingState) {
            getInputs() >> Optional.of(inputs)
        }
        def adapter = new SnapshotTaskInputsBuildOperationResult(cachingState)

        when:
        inputs.inputValueFingerprints >> ImmutableSortedMap.copyOf(b: HashCode.fromInt(0x000000bb), a: HashCode.fromInt(0x000000aa))
        inputs.inputFileFingerprints >> ImmutableSortedMap.copyOf(c: { getHash: { HashCode.fromInt(0x000000cc) } } as CurrentFileCollectionFingerprint)

        then:
        adapter.inputValueHashesBytes.collectEntries { [(it.key):HashCode.fromBytes(it.value).toString()] } == [a: "000000aa", b: "000000bb"]

        when:
        inputs.nonCacheableInputProperties >> ImmutableSortedSet.of("bean", "someOtherBean")
        then:
        adapter.inputPropertiesLoadedByUnknownClassLoader == ["bean", "someOtherBean"] as SortedSet

        when:
        inputs.implementation >> ImplementationSnapshot.of("org.gradle.TaskType", HashCode.fromInt(0x000000cc))
        then:
        HashCode.fromBytes(adapter.classLoaderHashBytes).toString() == "000000cc"

        when:
        inputs.additionalImplementations >> ImmutableList.copyOf([ImplementationSnapshot.of("foo", HashCode.fromInt(0x000000ee)), ImplementationSnapshot.of("bar", HashCode.fromInt(0x000000dd))])
        then:
        adapter.actionClassLoaderHashesBytes.collect{ HashCode.fromBytes(it).toString() } == ["000000ee", "000000dd"]
        adapter.actionClassNames == ["foo", "bar"]

        when:
        inputs.outputProperties >> ImmutableSortedSet.copyOf(["2", "1"])
        then:
        adapter.outputPropertyNames == ["1", "2"]
    }

}
