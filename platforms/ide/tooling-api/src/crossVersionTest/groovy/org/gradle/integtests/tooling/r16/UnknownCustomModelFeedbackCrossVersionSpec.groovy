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

package org.gradle.integtests.tooling.r16


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.UnknownModelException

class UnknownCustomModelFeedbackCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=5.1")
    def "fails gracefully when unknown model requested"() {
        when:
        withConnection {
            it.model(CustomModel.class).get()
        }

        then:
        UnknownModelException e = thrown()
        e.message == "No model of type 'CustomModel' is available in this build."

        and:
        failure.assertHasDescription("No builders are available to build a model of type 'org.gradle.integtests.tooling.r16.CustomModel'.")
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=3.0 <5.1")
    def "fails gracefully when unknown model requested for version that does not log failure"() {
        when:
        withConnection {
            it.model(CustomModel.class).get()
        }

        then:
        UnknownModelException e = thrown()
        e.message == "No model of type 'CustomModel' is available in this build."
    }
}
