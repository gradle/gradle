/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.internal.consumer.connection.ModelBuilderBackedConsumerConnection
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion

@ToolingApiVersion("current")
@TargetGradleVersion(">=1.6")
class VersionDetailsCrossVersionSpec extends ToolingApiSpecification {

    def "VersionDetails supports expected models"() {
        when:
        VersionDetails version = ModelBuilderBackedConsumerConnection.getVersionDetails(targetDist.version.version)

        then:
        version.isModelSupported(GradleBuild) == targetDist.version.compareTo(targetDist.version.version("1.8")) >= 0
        version.isModelSupported(BuildInvocations) == targetDist.version.compareTo(targetDist.version.version("1.11")) > 0
        version.supportsGradleProjectModel() == targetDist.version.compareTo(targetDist.version.version("1.6")) >= 0
    }
}
