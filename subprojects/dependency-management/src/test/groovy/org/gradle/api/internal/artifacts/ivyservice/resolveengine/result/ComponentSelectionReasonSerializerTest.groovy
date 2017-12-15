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

    def "serializes custom reasons"() {
        expect:
        check(VersionSelectionReasons.CONFLICT_RESOLUTION.withReason("my conflict resolution"))
        check(VersionSelectionReasons.CONFLICT_RESOLUTION_BY_RULE.withReason("my conflict resolution by rule"))
        check(VersionSelectionReasons.FORCED.withReason("forced by me"))
        check(VersionSelectionReasons.REQUESTED.withReason("I really asked for it"))
        check(VersionSelectionReasons.ROOT.withReason("I know this is the root of the graph"))
        check(VersionSelectionReasons.SELECTED_BY_RULE.withReason("Wouldn't it be nice to add custom reasons?"))
    }

    def "multiple writes of the same custom reason"() {
        when:
        def firstTime = toBytes(withReason("hello"), serializer)
        def secondTime = toBytes(withReason("hello"), serializer)
        def thirdTime = toBytes(withReason("hello"), serializer)
        def fourthTime = toBytes(withReason("how are you?"), serializer)
        def fifthTime = toBytes(withReason("hello"), serializer)

        then: "first serialization is more expensive than the next one"
        firstTime.length > secondTime.length

        and: "subsequent serializations use the same amount of data"
        secondTime.length == thirdTime.length

        and: "adding a different reason will imply serializing the reason"
        fourthTime.length > thirdTime.length

        and: "remembers the selection reasons"
        fifthTime.length == thirdTime.length
    }

    def "yields informative message on incorrect input"() {
        when:
        check(Mock(ComponentSelectionReason))
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

    private static ComponentSelectionReasonInternal withReason(String reason) {
        VersionSelectionReasons.SELECTED_BY_RULE.withReason(reason)
    }

}
