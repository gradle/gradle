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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.UnknownModelException

class UnknownCustomModelFeedbackCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion("current")
    @TargetGradleVersion(">=1.6")
    def "fails gracefully when unknown model requested when custom models are supported by the target version"() {
        when:
        withConnection { it.getModel(CustomModel.class) }

        then:
        UnknownModelException e = thrown()
        e.message == "No model of type 'CustomModel' is available in this build."
    }

    @ToolingApiVersion("current")
    @TargetGradleVersion(">=1.2 <1.6")
    def "fails gracefully when unknown model requested when custom models are not supported by the target version"() {
        when:
        withConnection { it.getModel(CustomModel.class) }

        then:
        UnknownModelException e = thrown()
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support building a model of type 'CustomModel'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions."
    }

    @ToolingApiVersion("!current")
    @TargetGradleVersion("current")
    def "fails gracefully when unknown model requested by old tooling API version"() {
        when:
        withConnection { it.getModel(CustomModel.class) }

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message.contains('CustomModel')
    }
}
