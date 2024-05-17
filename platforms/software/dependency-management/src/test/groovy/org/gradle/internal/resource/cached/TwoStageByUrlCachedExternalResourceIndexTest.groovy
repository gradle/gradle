/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.resource.cached

import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class TwoStageByUrlCachedExternalResourceIndexTest extends Specification {
    Path readOnlyPath = Stub(Path)
    File artifact = Stub(File)
    ExternalResourceMetaData metadata = Stub(ExternalResourceMetaData)

    CachedExternalResourceIndex<String> readIndex = Mock(CachedExternalResourceIndex)
    CachedExternalResourceIndex<String> writeIndex = Mock(CachedExternalResourceIndex)

    @Subject
    TwoStageByUrlCachedExternalResourceIndex twoStageIndex = new TwoStageByUrlCachedExternalResourceIndex(readOnlyPath, readIndex, writeIndex)

    def "storing delegates to the write index"() {
        when:
        twoStageIndex.store("key", artifact, metadata)

        then:
        1 * writeIndex.store("key", artifact, metadata)
        0 * readIndex._
    }

    def "store missing delegates to the write index"() {
        when:
        twoStageIndex.storeMissing("key")

        then:
        1 * writeIndex.storeMissing("key")
        0 * readIndex._
    }

    def "lookup searches the write index then delegates to the read index"() {
        when:
        twoStageIndex.lookup("key")

        then:
        1 * writeIndex.lookup("key") >> null
        1 * readIndex.lookup("key")

        when:
        twoStageIndex.lookup("other")

        then:
        1 * writeIndex.lookup("other") >> Stub(CachedExternalResource)
        0 * readIndex._
    }

    def "clearing delegates to the writable cache"() {
        when:
        twoStageIndex.clear("key")

        then:
        1 * writeIndex.clear("key")
        0 * readIndex._
    }
}
