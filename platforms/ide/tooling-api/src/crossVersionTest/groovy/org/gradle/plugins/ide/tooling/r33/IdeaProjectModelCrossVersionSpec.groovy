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

package org.gradle.plugins.ide.tooling.r33

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaProject

@TargetGradleVersion('>=3.3')
class IdeaProjectModelCrossVersionSpec extends ToolingApiSpecification {

    def "Idea modules are returned in order"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = 'root'
            include 'a'
            include 'b'
        """

        when:
        def ideaProject = loadToolingModel(IdeaProject)

        then:
        ideaProject.modules*.name == ['root', 'a', 'b']
    }
}
