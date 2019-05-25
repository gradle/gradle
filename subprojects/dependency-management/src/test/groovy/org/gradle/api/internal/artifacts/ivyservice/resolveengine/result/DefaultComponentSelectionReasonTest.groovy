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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.internal.Describables
import spock.lang.Specification

class DefaultComponentSelectionReasonTest extends Specification {

    def "requested only selection reason is expected"() {
        when:
        def reason = ComponentSelectionReasons.requested()

        then:
        reason.isExpected()
    }

    def "root only selection reason is expected"() {
        when:
        def reason = ComponentSelectionReasons.root()

        then:
        reason.isExpected()
    }

    def "requested with other selection reason is not expected"() {
        given:
        def reason = ComponentSelectionReasons.requested()

        when:
        addCause(reason, ComponentSelectionCause.CONFLICT_RESOLUTION, "test")

        then:
        !reason.isExpected()
    }

    def "other selection reason and requested is not expected"() {
        when:
        def reason = ComponentSelectionReasons.of(new DefaultComponentSelectionDescriptor(ComponentSelectionCause.REQUESTED), new DefaultComponentSelectionDescriptor(ComponentSelectionCause.FORCED))

        then:
        !reason.isExpected()
    }

    def addCause(ComponentSelectionReason reason, ComponentSelectionCause cause, String description) {
        ((ComponentSelectionReasonInternal) reason).addCause(cause, Describables.of(description))
    }
}
