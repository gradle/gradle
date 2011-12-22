/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m7

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.internal.consumer.DefaultModelBuilder
import org.gradle.tooling.model.internal.TestModel

@MinToolingApiVersion('1.0-milestone-7')
@MinTargetGradleVersion('1.0-milestone-5')
class UnknownModelImprovedFeedbackIntegrationTest extends ToolingApiSpecification {

    def "fails gracefully when building model unknown to given provider"() {
        when:
        def e = maybeFailWithConnection { it.getModel(TestModel.class) }

        then:
        e.message.contains(DefaultModelBuilder.INCOMPATIBLE_VERSION_HINT)
    }
}
