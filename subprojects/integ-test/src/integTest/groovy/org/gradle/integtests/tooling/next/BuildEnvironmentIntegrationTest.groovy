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

package org.gradle.integtests.tooling.next

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.BuildEnvironment

@MinToolingApiVersion(currentOnly = true)
@MinTargetGradleVersion(currentOnly = true)
class BuildEnvironmentIntegrationTest extends ToolingApiSpecification {

    def "informs about build environment"() {
        //for debugging purposes:
        //toolingApi.isEmbedded = false

        when:
        dist.file('build.gradle')  << "task foo"

        then:
        withConnection { ProjectConnection connection ->
            def model = connection.getModel(BuildEnvironment.class)
            model.gradleVersion == dist.version
        }
    }
}
