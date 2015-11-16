/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.r12rc1

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
import org.gradle.tooling.model.eclipse.EclipseProject

@TargetGradleVersion(">=1.0-milestone-8 <=1.1")
class UnsupportedOperationFeedbackCrossVersionSpec extends ToolingApiSpecification {
    def "fails when attempting to run tasks when building a model"() {
        when:
        withConnection { ProjectConnection connection ->
            connection.model(EclipseProject.class).forTasks('eclipse').get()
        }

        then:
        UnsupportedOperationConfigurationException e = thrown()
        e.message.contains("The version of Gradle you are using (${targetDist.version.version}) does not support the ModelBuilder API forTasks() configuration option. Support for this is available in Gradle 1.2 and all later versions.")
    }
}
