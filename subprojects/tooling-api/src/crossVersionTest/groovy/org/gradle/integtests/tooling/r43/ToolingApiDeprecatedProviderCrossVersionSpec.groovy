/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion("current")
class ToolingApiDeprecatedProviderCrossVersionSpec extends ToolingApiVersionSpecification {
    @TargetGradleVersion(">=1.2 <2.6")
    def "deprecation warning in build execution for >=1.2 and <2.6 providers"() {
        when:
        build()

        then:
        output.count(providerDeprecationMessage(targetDist.version.version)) == 1
    }

    @TargetGradleVersion(">=1.2 <2.6")
    def "deprecation warning in model retrieval for >=1.2 and <2.6 providers"() {
        when:
        getModel()

        then:
        output.count(providerDeprecationMessage(targetDist.version.version)) == 1
    }

    @TargetGradleVersion(">=1.8 <2.6")
    def "deprecation warning in build action execution for >=1.8 and <2.6 providers"() {
        when:
        buildAction()

        then:
        output.count(providerDeprecationMessage(targetDist.version.version)) == 1
    }

    // No test for test action since it was introduced in 2.6
}
