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

import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.internal.Describables
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec

class ComponentSelectionReasonSerializerTest extends SerializerSpec {

    private serializer = new ComponentSelectionReasonSerializer()

    def "serializes"() {
        expect:
        check(VersionSelectionReasons.CONFLICT_RESOLUTION)
        check(VersionSelectionReasons.FORCED)
        check(VersionSelectionReasons.REQUESTED)
        check(VersionSelectionReasons.ROOT)
        check(VersionSelectionReasons.SELECTED_BY_RULE)
        check(VersionSelectionReasons.REQUESTED, VersionSelectionReasons.SELECTED_BY_RULE)
    }

    def "serializes custom reasons"() {
        expect:
        check(VersionSelectionReasons.CONFLICT_RESOLUTION.withReason(Describables.of("my conflict resolution")))
        check(VersionSelectionReasons.FORCED.withReason(Describables.of("forced by me")))
        check(VersionSelectionReasons.REQUESTED.withReason(Describables.of("I really asked for it")))
        check(VersionSelectionReasons.ROOT.withReason(Describables.of("I know this is the root of the graph")))
        check(VersionSelectionReasons.SELECTED_BY_RULE.withReason(Describables.of("Wouldn't it be nice to add custom reasons?")))
        check(VersionSelectionReasons.REQUESTED, VersionSelectionReasons.SELECTED_BY_RULE.withReason(Describables.of("More details!")))
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

    void check(ComponentSelectionDescriptor... reasons) {
        def reason = VersionSelectionReasons.of(Arrays.asList(reasons))
        def result = serialize(reason, serializer)
        assert result == reason
    }

    private static ComponentSelectionReasonInternal withReason(String reason) {
        VersionSelectionReasons.of([VersionSelectionReasons.SELECTED_BY_RULE.withReason(Describables.of(reason))])
    }

    @Override
    def <T> T serialize(T value, Serializer<T> serializer) {
        def bytes = toBytes(value, serializer)
        serializer.reset()
        return fromBytes(bytes, serializer)
    }
}
