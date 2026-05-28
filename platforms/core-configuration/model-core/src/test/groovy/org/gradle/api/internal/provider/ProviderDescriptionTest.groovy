/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.internal.logging.text.TreeFormatter
import spock.lang.Specification

class ProviderDescriptionTest extends Specification {

    def "stores kind, state, display name, sources and kind-specific data"() {
        given:
        def child = ProviderDescription.unknown("inner", false)
        def desc = new ProviderDescription(
            ProviderDescription.Kind.VALUE_SOURCE,
            false,
            "FOO",
            ImmutableList.of(child),
            ImmutableMap.of("valueSourceType", "EnvironmentVariableValueSource")
        )

        expect:
        desc.kind() == ProviderDescription.Kind.VALUE_SOURCE
        !desc.hasValue()
        desc.displayName() == "FOO"
        desc.sources() == [child]
        desc.kindSpecificData() == ["valueSourceType": "EnvironmentVariableValueSource"]
    }

    def "unknown() factory builds a trivial UNKNOWN description"() {
        when:
        def desc = ProviderDescription.unknown("this property", true)

        then:
        desc.kind() == ProviderDescription.Kind.UNKNOWN
        desc.hasValue()
        desc.displayName() == "this property"
        desc.sources().isEmpty()
        desc.kindSpecificData().isEmpty()
    }

    def "renderMissingChainTo returns false for a trivial UNKNOWN description"() {
        given:
        def desc = ProviderDescription.unknown("some provider", false)
        def formatter = new TreeFormatter()
        formatter.node("root")

        when:
        def rendered = desc.renderMissingChainTo(formatter)

        then:
        !rendered
        formatter.toString() == "root"
    }

    def "renderMissingChainTo renders a single non-UNKNOWN node"() {
        given:
        def desc = new ProviderDescription(
            ProviderDescription.Kind.PROPERTY,
            false,
            "myProp",
            ImmutableList.of(),
            ImmutableMap.of()
        )
        def formatter = new TreeFormatter()

        when:
        def rendered = desc.renderMissingChainTo(formatter)

        then:
        rendered
        formatter.toString().contains("property 'myProp'")
    }

    def "renderMissingChainTo descends into every missing source and skips present ones"() {
        given:
        def presentSrc = new ProviderDescription(
            ProviderDescription.Kind.PROPERTY, true, "presentA",
            ImmutableList.of(), ImmutableMap.of()
        )
        def missingSrcA = new ProviderDescription(
            ProviderDescription.Kind.PROPERTY, false, "missingA",
            ImmutableList.of(), ImmutableMap.of()
        )
        def missingSrcB = new ProviderDescription(
            ProviderDescription.Kind.PROPERTY, false, "missingB",
            ImmutableList.of(), ImmutableMap.of()
        )
        def desc = new ProviderDescription(
            ProviderDescription.Kind.ZIP, false, "zipped",
            ImmutableList.of(presentSrc, missingSrcA, missingSrcB),
            ImmutableMap.of()
        )
        def formatter = new TreeFormatter()

        when:
        desc.renderMissingChainTo(formatter)

        then:
        def msg = formatter.toString()
        msg.contains("zip 'zipped'")
        msg.contains("missingA")
        msg.contains("missingB")
        !msg.contains("presentA")
    }

    def "renderMissingChainTo renders kind-specific data"() {
        given:
        def desc = new ProviderDescription(
            ProviderDescription.Kind.VALUE_SOURCE,
            false,
            null,
            ImmutableList.of(),
            ImmutableMap.of("valueSourceType", "EnvironmentVariableValueSource", "variableName", "FOO")
        )
        def formatter = new TreeFormatter()

        when:
        desc.renderMissingChainTo(formatter)

        then:
        def msg = formatter.toString()
        msg.contains("value source")
        msg.contains("valueSourceType: EnvironmentVariableValueSource")
        msg.contains("variableName: FOO")
    }
}
