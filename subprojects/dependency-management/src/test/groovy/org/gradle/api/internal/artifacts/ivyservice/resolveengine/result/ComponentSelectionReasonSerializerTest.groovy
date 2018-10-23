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
import org.gradle.internal.serialize.AbstractDecoder
import org.gradle.internal.serialize.AbstractEncoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedDecoder
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder

class ComponentSelectionReasonSerializerTest extends SerializerSpec {
    private final static ComponentSelectionDescriptorInternal[] REASONS_FOR_TEST = [
            VersionSelectionReasons.SELECTED_BY_RULE,
            VersionSelectionReasons.CONFLICT_RESOLUTION,
            VersionSelectionReasons.CONSTRAINT,
            VersionSelectionReasons.REJECTION,
            VersionSelectionReasons.FORCED
    ] as ComponentSelectionDescriptorInternal[]


    private serializer = new ComponentSelectionReasonSerializer()

    @Override
    Class<? extends AbstractEncoder> getEncoder() {
        StringDeduplicatingKryoBackedEncoder
    }

    @Override
    Class<? extends AbstractDecoder> getDecoder() {
        StringDeduplicatingKryoBackedDecoder
    }

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
        def single = toBytes(withReason("hello"), serializer)
        def withDuplicate = toBytes(withReasons("hello", "hello"), serializer)
        def withoutDuplicate = toBytes(withReasons("hello", "other"), serializer)

        then:
        single.length < withDuplicate.length
        withDuplicate.length < 2*single.length
        withoutDuplicate.length > withDuplicate.length
    }

    void check(ComponentSelectionDescriptor... reasons) {
        def reason = VersionSelectionReasons.of(Arrays.asList(reasons))
        def result = serialize(reason, serializer)
        assert result == reason
    }

    private static ComponentSelectionReasonInternal withReason(String reason) {
        VersionSelectionReasons.of([VersionSelectionReasons.SELECTED_BY_RULE.withReason(Describables.of(reason))])
    }

    private static ComponentSelectionReasonInternal withReasons(String... reasons) {
        int idx = -1
        VersionSelectionReasons.of(reasons.collect {
            reason(++idx).withReason(Describables.of(it))
        })
    }

    private static ComponentSelectionDescriptorInternal reason(int idx) {
        REASONS_FOR_TEST[idx%REASONS_FOR_TEST.length]
    }

    @Override
    def <T> T serialize(T value, Serializer<T> serializer) {
        def bytes = toBytes(value, serializer)
        return fromBytes(bytes, serializer)
    }
}
