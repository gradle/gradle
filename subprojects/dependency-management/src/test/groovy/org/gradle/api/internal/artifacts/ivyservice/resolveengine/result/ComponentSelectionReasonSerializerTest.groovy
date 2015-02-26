/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.SerializerSpec

class ComponentSelectionReasonSerializerTest extends SerializerSpec {

    private serializer = new ComponentSelectionReasonSerializer()

    def "serializes"() {
        expect:
        check(VersionSelectionReasons.CONFLICT_RESOLUTION)
        check(VersionSelectionReasons.CONFLICT_RESOLUTION_BY_RULE)
        check(VersionSelectionReasons.FORCED)
        check(VersionSelectionReasons.REQUESTED)
        check(VersionSelectionReasons.ROOT)
        check(VersionSelectionReasons.SELECTED_BY_RULE)
    }

    def "yields informative message on incorrect input"() {
        when:
        check({} as ComponentSelectionReason)
        then:
        thrown(IllegalArgumentException)

        when:
        serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream("foo".bytes)))

        then:
        thrown(IllegalArgumentException)
    }

    void check(ComponentSelectionReason reason) {
        def result = serialize(reason, serializer)
        assert result == reason
    }
}
