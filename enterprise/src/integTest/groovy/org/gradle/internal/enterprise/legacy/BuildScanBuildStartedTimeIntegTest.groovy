/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.legacy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.scan.time.BuildScanBuildStartedTime

class BuildScanBuildStartedTimeIntegTest extends AbstractIntegrationSpec {

    def "can access build scan build started time"() {
        when:
        buildFile << """
            def time = project.services.get($BuildScanBuildStartedTime.name).buildStartedTime
            def timer = project.services.get($BuildStartedTime.name)
            assert timer.startTime == time

            println "timestamp: \$time"
        """

        succeeds("help")

        then:
        output.contains("timestamp: ")
    }
}
