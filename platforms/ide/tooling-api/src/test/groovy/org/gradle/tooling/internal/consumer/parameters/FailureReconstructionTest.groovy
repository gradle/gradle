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

package org.gradle.tooling.internal.consumer.parameters

import org.gradle.tooling.internal.protocol.FailureDescriptionReconstructor
import org.gradle.tooling.internal.protocol.InternalFailure
import spock.lang.Specification

import java.util.function.Function

class FailureReconstructionTest extends Specification {

    def "preserves the original line terminators instead of normalizing them to the platform separator"() {
        // The printer writes a failure's message verbatim, then its own line separator before the frames. When the
        // message embeds a newline that differs from the frame separator (e.g. a multi-line message on Windows, where
        // frames end with \r\n), reconstruction must reproduce both terminators rather than rewrite them, or the text
        // stops matching a direct print. A single node with no causes must round-trip its own description unchanged.
        def own = "java.lang.IllegalStateException: inner\n\tindented detail\r\n\tat Foo.bar(Foo.java:1)\r\n"

        when:
        def result = FailureDescriptionReconstructor.reconstruct(own, { it } as Function, { [] } as Function)

        then:
        result == own
    }

    def "elides each cause's common frame tail against the parent across the multiple-cause branch"() {
        def nl = System.lineSeparator()
        def shared = "${nl}\tat Shared.a(S.java:9)${nl}\tat Shared.b(S.java:10)"
        def root = node("java.lang.RuntimeException: multi${nl}\tat Root.run(R.java:1)${shared}${nl}", [
            node("java.lang.RuntimeException: one${nl}\tat One.x(One.java:1)${shared}${nl}", []),
            node("java.lang.RuntimeException: two${nl}\tat Two.y(Two.java:1)${shared}${nl}", []),
        ])

        when:
        def text = FailureDescriptionReconstructor.reconstruct(root, { it.own } as Function, { it.causes } as Function)

        then: "each cause keeps only its own distinct frame and elides the two it shares with the parent"
        text.contains("Cause 1: java.lang.RuntimeException: one${nl}\tat One.x(One.java:1)${nl}\t... 2 more")
        text.contains("Cause 2: java.lang.RuntimeException: two${nl}\tat Two.y(Two.java:1)${nl}\t... 2 more")

        and: "the shared frames are printed once under the parent, not repeated under either cause"
        text.count("\tat Shared.a(S.java:9)") == 1
    }

    private static Map node(String own, List causes) {
        [own: own, causes: causes]
    }

    def "rebuilds each node from its own description and preserves the cause tree"() {
        def leaf = internalFailure("java.lang.IllegalStateException: inner\n\tat Inner.run(Inner.java:5)", [])
        def root = internalFailure("java.lang.RuntimeException: boom\n\tat Root.run(Root.java:9)", [leaf])

        when:
        def failures = BuildProgressListenerAdapter.toFailures([root])

        then:
        failures.size() == 1
        def rebuiltRoot = failures[0]
        rebuiltRoot.message == "msg"
        rebuiltRoot.causes.size() == 1
        rebuiltRoot.causes[0].message == "msg"

        and: "the full description is reassembled from each node's own text, not from the daemon's per-node full text"
        rebuiltRoot.description.contains("java.lang.RuntimeException: boom")
        rebuiltRoot.description.contains("Caused by: java.lang.IllegalStateException: inner")
        !rebuiltRoot.description.contains("FULL-NOT-USED")
    }

    def "falls back to the full description when the daemon has no own description (#error.class.simpleName)"() {
        def origFailure = Stub(InternalFailure) {
            getMessage() >> "boom"
            getOwnDescription() >> { throw error }
            getDescription() >> "FULL TEXT FROM OLD DAEMON"
            getCauses() >> []
            getProblems() >> []
        }

        when:
        def failures = BuildProgressListenerAdapter.toFailures([origFailure])

        then:
        failures[0].description == "FULL TEXT FROM OLD DAEMON"

        where:
        // An old daemon's InternalFailure implements the interface without the method (AbstractMethodError) or
        // predates it entirely (NoSuchMethodError); the consumer must fall back in both cases.
        error << [new AbstractMethodError("old daemon"), new NoSuchMethodError("old daemon")]
    }

    def "does not read the full per-node description when own descriptions are available"() {
        def leaf = Mock(InternalFailure)
        def root = Mock(InternalFailure)

        when:
        BuildProgressListenerAdapter.toFailures([root])

        then:
        _ * root.getOwnDescription() >> "java.lang.RuntimeException: boom\n\tat Root.run(Root.java:9)"
        _ * root.getCauses() >> [leaf]
        _ * root.getMessage() >> "boom"
        _ * root.getProblems() >> []
        _ * leaf.getOwnDescription() >> "java.lang.IllegalStateException: inner\n\tat Inner.run(Inner.java:5)"
        _ * leaf.getCauses() >> []
        _ * leaf.getMessage() >> "inner"
        _ * leaf.getProblems() >> []
        0 * root.getDescription()
        0 * leaf.getDescription()
    }

    private InternalFailure internalFailure(String own, List<InternalFailure> causes) {
        Stub(InternalFailure) {
            getMessage() >> "msg"
            getOwnDescription() >> own
            getDescription() >> "FULL-NOT-USED"
            getCauses() >> causes
            getProblems() >> []
        }
    }
}
