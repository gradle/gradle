/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r930.FetchBuildActionCrossVersionSpec.FetchGradleBuildAction

@ToolingApiVersion(">=9.3.0")
@TargetGradleVersion(">=9.4.0")
class FetchBuildActionCrossVersionSpec extends ToolingApiSpecification {

    def "returns a failure for GradleBuild model if settings script fails due to #description"() {
        given:
        settingsFile.delete()
        settingsKotlinFile << """
            ${error}
        """

        when:
        def result = succeeds {
            action(new FetchGradleBuildAction())
                .run()
        }

        then:
        result.modelValue != null
        result.causes.size() == 1
        result.causes[0].contains(cause)

        where:
        description          | error                                                        | cause
        "script compilation" | "broken !!!"                                                 | "broken !!!"
        "runtime exception"  | """throw RuntimeException("broken settings script")"""       | "broken settings script"
    }
}
