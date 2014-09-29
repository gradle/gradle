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
package org.gradle.integtests.tooling.r22

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.idea.*

class Idea13ModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.2")
    @ToolingApiVersion(">=2.2")
    def "provides generated sources dir information"() {

        file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea {
  module {
    sourceDirs += file('foo')
    testSourceDirs += file('foo2')
    generatedSourceDirs += file('foo')
    generatedSourceDirs += file('foo2')
  }
}
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children[0]

        then:
        module.contentRoots[0].sourceDirectories.any { it.generated && it.directory.path.endsWith('foo') }
        module.contentRoots[0].testDirectories.any { it.generated && it.directory.path.endsWith('foo2') }
    }
}