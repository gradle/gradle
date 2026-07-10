/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.tooling.internal.provider.continuous

import org.gradle.deployment.internal.PendingChangesListener
import spock.lang.Specification

class SingleFirePendingChangesListenerTest extends Specification {
    def delegate = Mock(PendingChangesListener)

    def "propagates changes only once"() {
        def singleFirePendingChangesListener = new SingleFirePendingChangesListener(delegate)

        when:
        singleFirePendingChangesListener.onPendingChanges()
        then:
        1 * delegate.onPendingChanges()

        when:
        singleFirePendingChangesListener.onPendingChanges()
        then:
        0 * delegate.onPendingChanges()
    }
}
