/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r31

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion('>=3.1')
@TargetGradleVersion(">=3.1")
class CompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def "Can query project models from a composite build"() {
        setup:

        file('my-lib', 'settings.gradle') << 'rootProject.name = "my-lib"'

        file('settings.gradle') << """
            rootProject.name = "root"
            includeBuild 'my-lib'
        """

        when:
        def models = withCompositeConnection(rootDir) { composite ->
            composite.getModels(GradleProject)
        }

        then:
        models*.model*.name == ['root', 'my-lib']
    }

}
