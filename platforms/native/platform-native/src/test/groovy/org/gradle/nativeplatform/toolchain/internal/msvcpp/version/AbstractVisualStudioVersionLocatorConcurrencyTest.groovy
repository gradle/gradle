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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.concurrent.ContentionGate

class AbstractVisualStudioVersionLocatorConcurrencyTest extends ConcurrentSpec {

    def "locates installs once when queried concurrently"() {
        def install = Stub(VisualStudioInstallCandidate)
        def gate = new ContentionGate()
        def results = [].asSynchronized()
        def locator = new AbstractVisualStudioVersionLocator() {
            @Override
            protected List<VisualStudioInstallCandidate> locateInstalls() {
                gate.pass { [install] }
            }

            @Override
            String getSource() {
                "test"
            }
        }

        when:
        async {
            start { results << locator.visualStudioInstalls }
            start { results << locator.visualStudioInstalls }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 1
        results == [[install], [install]]
    }
}
