/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.tooling.BuildLauncher

class ContinuousBuildProgressEventsCrossVersionSpec extends ContinuousBuildToolingApiSpecification {

    def events = ProgressEvents.create()

    void customizeLauncher(BuildLauncher launcher) {
        launcher.addProgressListener(events)
    }

    def "client can receive appropriate logging and progress events for subsequent builds"() {
        when:
        def javaSrcFile = sourceDir.file("Thing.java") << 'public class Thing {}'

        then:
        runBuild {
            succeeds()
            receivedBuildEvents()
            waitBeforeModification javaSrcFile
            javaSrcFile.setText('public class Thing { public static final int FOO = 1; }')
            succeeds()
            receivedBuildEvents()
            cancel()
        }
    }

    void receivedBuildEvents() {
        events.assertIsABuild()
        events.clear()
    }
}
